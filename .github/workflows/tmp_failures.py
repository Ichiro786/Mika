import glob, xml.etree.ElementTree as ET, sys
for path in sys.argv[1:]:
    for f in glob.glob(path + '/**/*.xml', recursive=True):
        try: root = ET.parse(f).getroot()
        except Exception: continue
        for tc in root.iter('testcase'):
            for fail in list(tc.findall('failure')) + list(tc.findall('error')):
                msg = (fail.get('message') or fail.text or '').replace('\n', ' | ')
                print(f"FAIL {tc.get('classname','').split('.')[-1]}.{tc.get('name')}: {msg[:600]}")
