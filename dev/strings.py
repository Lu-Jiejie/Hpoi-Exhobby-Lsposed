import sys
import zipfile

apk = sys.argv[1]
words = sys.argv[2:]
data = zipfile.ZipFile(apk).read('classes.dex')
for w in words:
    print(w, w.encode('utf-8') in data)
