# Native packet tools

tcpdump 4.99.7 and statically linked libpcap 1.10.7 are built from the official
stable source archives. Their BSD license texts and source-header notices are
included here. Upstream sources and Aurora's build adapter are retained in the
corresponding-source artifact; hashes are fixed in packet/Capture.cmake.

The programs run as independent processes. Packaging does not establish Root
capture support, successful packet collection, traffic attribution or decryption.
