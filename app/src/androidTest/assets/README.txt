test-pattern.ts is a synthetic 20-second H.264 test pattern with no audio, used only by
Android instrumentation tests and never bundled in the production APK. It was generated with:

ffmpeg -f lavfi -i 'testsrc2=size=320x180:rate=15' -t 20 -c:v libx264 \
  -profile:v baseline -level:v 3.0 -pix_fmt yuv420p -b:v 100k -g 30 \
  -preset veryfast -an -f mpegts test-pattern.ts
