// Package wabridge links Orbit to the user's WhatsApp account as a linked device, using whatsmeow.
//
// It is built into an Android library with gomobile, so its API only uses strings, numbers,
// booleans, errors and one callback interface. Orbit decides which chats it may read or answer;
// this package only connects, reports messages and sends text when asked.
package wabridge

import (
	"context"
	"database/sql"
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"sort"
	"strings"
	"sync"
	"time"

	"go.mau.fi/whatsmeow"
	"go.mau.fi/whatsmeow/proto/waE2E"
	"go.mau.fi/whatsmeow/store"
	"go.mau.fi/whatsmeow/store/sqlstore"
	"go.mau.fi/whatsmeow/types"
	"go.mau.fi/whatsmeow/types/events"
	waLog "go.mau.fi/whatsmeow/util/log"
	"google.golang.org/protobuf/proto"
	_ "modernc.org/sqlite"
)

// Listener receives connection changes and messages. Orbit implements it in Kotlin.
type Listener interface {
	// OnState reports one of: connecting, connected, disconnected, logged_out, banned, outdated, paired, failed.
	OnState(state string, detail string)
	// OnQr gives a new QR code to show while linking by QR.
	OnQr(code string)
	// OnMessage gives one incoming or own-device message as JSON (see Message).
	OnMessage(message string)
}

// Message is the JSON shape passed to OnMessage.
type Message struct {
	ID         string `json:"id"`
	Chat       string `json:"chat"`
	ChatName   string `json:"chatName"`
	Sender     string `json:"sender"`
	SenderName string `json:"senderName"`
	Text       string `json:"text"`
	Time       int64  `json:"time"`
	FromMe     bool   `json:"fromMe"`
	Group      bool   `json:"group"`
	// Self is true for the user's own "Message yourself" chat.
	Self bool `json:"self"`
}

// Chat is one entry of Chats().
type Chat struct {
	JID   string `json:"jid"`
	Name  string `json:"name"`
	Group bool   `json:"group"`
	Self  bool   `json:"self"`
}

var (
	mu        sync.Mutex
	container *sqlstore.Container
	client    *whatsmeow.Client
	listener  Listener
	dbPath    string
)

const deviceName = "Orbit"

// Open prepares the session store in dir and connects if a device is already linked.
// It can be called again; later calls only reconnect.
func Open(dir string, l Listener) error {
	mu.Lock()
	defer mu.Unlock()
	listener = l
	if client != nil {
		if client.Store.ID != nil && !client.IsConnected() {
			go connect(client)
		}
		return nil
	}
	if err := os.MkdirAll(dir, 0o700); err != nil {
		return err
	}
	dbPath = filepath.Join(dir, "session.db")
	db, err := sql.Open("sqlite", "file:"+dbPath+"?_pragma=foreign_keys(1)&_pragma=busy_timeout(5000)&_pragma=journal_mode(WAL)")
	if err != nil {
		return err
	}
	db.SetMaxOpenConns(1)
	c := sqlstore.NewWithDB(db, "sqlite3", waLog.Noop)
	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()
	if err = c.Upgrade(ctx); err != nil {
		_ = db.Close()
		return fmt.Errorf("session store: %w", err)
	}
	device, err := c.GetFirstDevice(ctx)
	if err != nil {
		_ = c.Close()
		return err
	}
	store.SetOSInfo(deviceName, [3]uint32{1, 0, 0})
	container = c
	client = whatsmeow.NewClient(device, waLog.Noop)
	client.EnableAutoReconnect = true
	client.AddEventHandler(handle)
	if device.ID != nil {
		go connect(client)
	}
	return nil
}

func connect(c *whatsmeow.Client) {
	emit("connecting", "")
	if err := c.Connect(); err != nil {
		emit("failed", err.Error())
	}
}

// IsLinked reports whether a device session is saved.
func IsLinked() bool {
	mu.Lock()
	defer mu.Unlock()
	return client != nil && client.Store.ID != nil
}

// IsConnected reports whether the socket is up and logged in.
func IsConnected() bool {
	mu.Lock()
	defer mu.Unlock()
	return client != nil && client.IsConnected() && client.IsLoggedIn()
}

// OwnJID is the linked account's phone-number address without the device part, or "".
func OwnJID() string {
	mu.Lock()
	defer mu.Unlock()
	if client == nil || client.Store.ID == nil {
		return ""
	}
	return client.Store.ID.ToNonAD().String()
}

// LinkWithPhone starts linking and returns the 8-character code to type in WhatsApp
// under Linked devices › Link with phone number instead.
func LinkWithPhone(phone string) (string, error) {
	c, err := freshClient()
	if err != nil {
		return "", err
	}
	ctx := context.Background()
	// The QR channel keeps the pairing socket open; the code is valid while it is.
	qr, err := c.GetQRChannel(ctx)
	if err != nil {
		return "", err
	}
	if err = c.Connect(); err != nil {
		return "", err
	}
	go drainQr(qr, false)
	// WhatsApp needs a moment after the socket opens before it accepts a phone pairing request.
	time.Sleep(1500 * time.Millisecond)
	return c.PairPhone(ctx, phone, true, whatsmeow.PairClientChrome, "Chrome (Linux)")
}

// LinkWithQr starts linking by QR; codes arrive through Listener.OnQr.
func LinkWithQr() error {
	c, err := freshClient()
	if err != nil {
		return err
	}
	qr, err := c.GetQRChannel(context.Background())
	if err != nil {
		return err
	}
	if err = c.Connect(); err != nil {
		return err
	}
	go drainQr(qr, true)
	return nil
}

func freshClient() (*whatsmeow.Client, error) {
	mu.Lock()
	defer mu.Unlock()
	if client == nil {
		return nil, errors.New("not opened")
	}
	if client.Store.ID != nil {
		return nil, errors.New("already linked")
	}
	client.Disconnect()
	return client, nil
}

func drainQr(ch <-chan whatsmeow.QRChannelItem, show bool) {
	for item := range ch {
		switch item.Event {
		case whatsmeow.QRChannelEventCode:
			if show {
				callQr(item.Code)
			}
		case "success":
			emit("paired", "")
		case "timeout":
			emit("failed", "timeout")
		default:
			if item.Error != nil {
				emit("failed", item.Error.Error())
			} else if item.Event != "" {
				emit("failed", item.Event)
			}
		}
	}
}

// CancelLink stops a linking attempt that has not finished.
func CancelLink() {
	mu.Lock()
	defer mu.Unlock()
	if client != nil && client.Store.ID == nil {
		client.Disconnect()
	}
}

// Disconnect closes the socket but keeps the session, so Open can reconnect later.
func Disconnect() {
	mu.Lock()
	defer mu.Unlock()
	if client != nil {
		client.Disconnect()
	}
}

// Reconnect opens the socket again for a linked session.
func Reconnect() {
	mu.Lock()
	defer mu.Unlock()
	if client != nil && client.Store.ID != nil && !client.IsConnected() {
		go connect(client)
	}
}

// Unlink removes Orbit from the account's linked devices and deletes the session from this phone.
func Unlink() error {
	mu.Lock()
	defer mu.Unlock()
	if client == nil {
		return nil
	}
	var logoutErr error
	if client.Store.ID != nil {
		ctx, cancel := context.WithTimeout(context.Background(), 20*time.Second)
		logoutErr = client.Logout(ctx)
		cancel()
		if logoutErr != nil {
			// Offline or already removed on the phone: still forget the session here.
			_ = client.Store.Delete(context.Background())
		}
	}
	client.Disconnect()
	if container != nil {
		_ = container.Close()
	}
	client, container = nil, nil
	for _, suffix := range []string{"", "-wal", "-shm"} {
		_ = os.Remove(dbPath + suffix)
	}
	return nil
}

// SendText sends text to a chat address and returns the new message ID.
func SendText(chat, text string) (string, error) {
	mu.Lock()
	c := client
	mu.Unlock()
	if c == nil || c.Store.ID == nil {
		return "", errors.New("not linked")
	}
	jid, err := types.ParseJID(chat)
	if err != nil {
		return "", err
	}
	ctx, cancel := context.WithTimeout(context.Background(), 45*time.Second)
	defer cancel()
	resp, err := c.SendMessage(ctx, jid.ToNonAD(), &waE2E.Message{Conversation: proto.String(text)})
	if err != nil {
		return "", err
	}
	return resp.ID, nil
}

// Chats lists saved contacts and joined groups as JSON, so the user can pick which ones Orbit may use.
func Chats() (string, error) {
	mu.Lock()
	c := client
	mu.Unlock()
	if c == nil || c.Store.ID == nil {
		return "[]", nil
	}
	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()
	own := c.Store.ID.ToNonAD()
	list := []Chat{{JID: own.String(), Name: "", Self: true}}
	if contacts, err := c.Store.Contacts.GetAllContacts(ctx); err == nil {
		for jid, info := range contacts {
			if jid.Server != types.DefaultUserServer || jid.User == own.User {
				continue
			}
			name := firstNonEmpty(info.FullName, info.FirstName, info.BusinessName, info.PushName)
			if name == "" {
				continue
			}
			list = append(list, Chat{JID: jid.String(), Name: name})
		}
	}
	if c.IsConnected() {
		if groups, err := c.GetJoinedGroups(ctx); err == nil {
			for _, g := range groups {
				list = append(list, Chat{JID: g.JID.String(), Name: g.Name, Group: true})
			}
		}
	}
	sort.SliceStable(list[1:], func(i, j int) bool {
		return strings.ToLower(list[i+1].Name) < strings.ToLower(list[j+1].Name)
	})
	out, err := json.Marshal(list)
	return string(out), err
}

func handle(raw interface{}) {
	switch evt := raw.(type) {
	case *events.Connected:
		emit("connected", "")
	case *events.Disconnected:
		emit("disconnected", "")
	case *events.LoggedOut:
		emit("logged_out", evt.Reason.String())
	case *events.StreamReplaced:
		emit("logged_out", "replaced")
	case *events.TemporaryBan:
		emit("banned", evt.String())
	case *events.ClientOutdated:
		emit("outdated", "")
	case *events.ConnectFailure:
		emit("failed", evt.Reason.String())
	case *events.PairSuccess:
		emit("paired", "")
	case *events.Message:
		onMessage(evt)
	}
}

func onMessage(evt *events.Message) {
	mu.Lock()
	c := client
	mu.Unlock()
	if c == nil || c.Store.ID == nil {
		return
	}
	text := messageText(evt.Message)
	if text == "" {
		return
	}
	info := evt.Info
	if info.Chat.Server == types.BroadcastServer || info.Chat.Server == types.NewsletterServer {
		return
	}
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	chat := toPhone(ctx, c, info.Chat.ToNonAD())
	sender := toPhone(ctx, c, info.Sender.ToNonAD())
	own := c.Store.ID.ToNonAD()
	self := !info.IsGroup && (chat.User == own.User || (!c.Store.LID.IsEmpty() && chat.User == c.Store.LID.User))
	if self {
		chat = own
	}
	m := Message{
		ID: info.ID, Chat: chat.String(), Sender: sender.String(), SenderName: info.PushName,
		Text: text, Time: info.Timestamp.UnixMilli(), FromMe: info.IsFromMe, Group: info.IsGroup, Self: self,
	}
	if info.IsGroup {
		if g, err := c.GetGroupInfo(ctx, info.Chat); err == nil {
			m.ChatName = g.Name
		}
	} else if contact, err := c.Store.Contacts.GetContact(ctx, chat); err == nil {
		m.ChatName = firstNonEmpty(contact.FullName, contact.FirstName, contact.BusinessName, contact.PushName)
	}
	if m.ChatName == "" && !info.IsFromMe {
		m.ChatName = info.PushName
	}
	out, err := json.Marshal(m)
	if err != nil {
		return
	}
	mu.Lock()
	l := listener
	mu.Unlock()
	if l != nil {
		l.OnMessage(string(out))
	}
}

// toPhone maps a hidden (LID) address to the phone-number address when WhatsApp has shared it.
func toPhone(ctx context.Context, c *whatsmeow.Client, jid types.JID) types.JID {
	if jid.Server != types.HiddenUserServer {
		return jid
	}
	if pn, err := c.Store.LIDs.GetPNForLID(ctx, jid); err == nil && !pn.IsEmpty() {
		return pn.ToNonAD()
	}
	return jid
}

// messageText reads plain text, link previews and media captions; other kinds become a short marker.
func messageText(m *waE2E.Message) string {
	if m == nil {
		return ""
	}
	switch {
	case m.GetConversation() != "":
		return m.GetConversation()
	case m.GetExtendedTextMessage().GetText() != "":
		return m.GetExtendedTextMessage().GetText()
	case m.GetImageMessage() != nil:
		return withMarker("[photo]", m.GetImageMessage().GetCaption())
	case m.GetVideoMessage() != nil:
		return withMarker("[video]", m.GetVideoMessage().GetCaption())
	case m.GetDocumentMessage() != nil:
		return withMarker("[file "+m.GetDocumentMessage().GetFileName()+"]", m.GetDocumentMessage().GetCaption())
	case m.GetAudioMessage() != nil:
		return "[voice message]"
	case m.GetStickerMessage() != nil:
		return "[sticker]"
	case m.GetLocationMessage() != nil:
		return "[location]"
	case m.GetContactMessage() != nil:
		return "[contact " + m.GetContactMessage().GetDisplayName() + "]"
	}
	return ""
}

func withMarker(marker, caption string) string {
	if caption == "" {
		return marker
	}
	return marker + " " + caption
}

func firstNonEmpty(values ...string) string {
	for _, v := range values {
		if strings.TrimSpace(v) != "" {
			return v
		}
	}
	return ""
}

func emit(state, detail string) {
	mu.Lock()
	l := listener
	mu.Unlock()
	if l != nil {
		l.OnState(state, detail)
	}
}

func callQr(code string) {
	mu.Lock()
	l := listener
	mu.Unlock()
	if l != nil {
		l.OnQr(code)
	}
}
