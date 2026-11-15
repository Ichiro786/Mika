---
name: amazon.com
display: Amazon
domains: ["amazon.com", "amazon.sg", "amazon.co.uk", "amazon.de", "amazon.co.jp"]
aliases: ["Amazon", "亚马逊"]
apps: []
open: amazon.com
mode: desktop
description: Global e-commerce: search products, prices, add to cart
---
## What it is

A global e-commerce platform. Each country site (.com, .sg, .co.uk, .de, .co.jp) is a separate storefront with its own prices, stock, currency and delivery.

## Pages and URLs

- Search: on the target country site open `/s?k=<keywords>`, keywords URL-encoded.
- Product detail: use the clean link keyed by ASIN. Cards on the search page may be rewritten by ads to redirect.
- When no region is specified, the site lands on a country site according to the visitor's location.

## Features

- **Search**: publicly viewable. Each result has ASIN, title, price and currency, rating and review count; ad slots are marked.
- **Product detail**: the buybox holds the main price, stock and seller terms; used and third-party offers are listed separately. For products with variants (color, size, etc.), the initial price corresponds only to the default selected variant; after selecting another variant, price and stock change.
- **Add to cart**: the Add to Cart button on the detail page; on success the page shows an Added to Cart notice.

## Known behavior

- Region redirects can make a page lack a price, or show currencies such as SGD or GBP.
- After adding to cart, the badge number and button color lag and may not update immediately.
- When the page layout changes or a human-verification check appears, product info cannot be read.
