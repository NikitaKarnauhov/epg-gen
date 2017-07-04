epg-gen
=======

`epg-gen` is a tool to generate [electronic program
guide](https://en.wikipedia.org/wiki/Electronic_Program_Guide) in
[XMLTV](http://xmltv.org) format for a given M3U8 playlist. It is intended to
be used by subscribers of Russian IPTV providers who want to supply EPG data
for their chosen media player software (e.g. [Kodi](https://kodi.tv) with [a
suitable IPTV addon](http://kodi.wiki/view/Add-on:IPTV_Simple_Client)).
Currently scrapers for <https://tv.yandex.ru> and <https://tv.mail.ru> are
implemented (hence mainly Russian-language channels are supported).

Usage
-----

Consider you have downloaded a `sample.m3u8` playlist from your IPTV provider.
It may look roughly like this (although with more items):

```
#EXTM3U
#EXTINF:0,РБК
#EXTGRP:новости
http://192.0.2.1/1
#EXTINF:0,Россия
#EXTGRP:новости
http://192.0.2.1/2
#EXTINF:0,Первый канал
#EXTGRP:другие
http://192.0.2.1/3
```

First you need to map channels found in the playlist to channels available from
supported scrapers using `epg-gen match` command:

```
> epg-gen match sample.m3u8
2 channel(s) matched
1 channel(s) not matched:
    Россия
```

Note that here we failed to match one of the channels. To fix this rerun with
`--interactive` (`-i`) and `--offline` (`-n`) options. In `interactive` mode
user input is requested every time when a channel cannot be matched with
sufficient confidence. Whereas `offline` mode suppresses re-downloading of
channel lists from upstream sources:

```
> epg-gen match -i -n sample.m3u8

Choose channel mapping for "Россия":
A. (skip all)
0. (skip)
1. "Россия 1" (mailru)
2. "Россия 1 HD" (mailru)
3. "Россия 1" (yandex)
4. "М тв россия" (yandex)
5. "Россия 1 HD" (yandex)
6. "Россия 24" (yandex)
7. "MTV Россия" (mailru)
8. "М тиви россия" (yandex)
9. "Мтиви россия" (yandex)
10. "Мтв россия" (yandex)
Enter (0 .. 10 or "A", default: 0): 3

3 channel(s) matched
```

You can review matched channels with `epg-gen list -m` command:

```
> epg-gen list -m
ID | name         | scraper | scraper ID | scraper name
 1 | РБК          |  mailru |       1126 | РБК
 3 | Первый канал |  yandex |        146 | Первый
 2 | Россия       |  yandex |        711 | Россия 1
```

After that EPG can be generated with `epg-gen build` command:

```
> epg-gen build xmltv.xml.gz
```

Result is compressed if output filename is given `.gz` extension. Note that
building XMLTV may take rather long time (depending on preferred scraper and
number of channels) since it implies downloading event descriptions for the
whole week. Downloaded data is cached to speed up rebuilding if channel list is
changed, etc.
