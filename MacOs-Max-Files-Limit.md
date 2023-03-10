# Raising the max-files limit on MacOS 13

MongoDB requires more file descriptors than the default on MacOS. 
The following worked for MacOS-13:

sudo vi /Library/LaunchDaemons/limit.maxfiles.plist

Add this to /Library/LaunchDaemons/limit.maxfiles.plist
```
<?xml version="1.0" encoding="UTF-8"?> 
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" 
 "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0"> 
 <dict>
 <key>Label</key>
 <string>limit.maxfiles</string>
 <key>ProgramArguments</key>
 <array>
 <string>launchctl</string>
 <string>limit</string>
 <string>maxfiles</string>
 <string>64000</string>
 <string>524288</string>
 </array>
 <key>RunAtLoad</key>
 <true/>
 <key>ServiceIPC</key>
 <false/>
 </dict>
</plist>
```

This will make the file’s owner be root:wheel, as needed. You then invoke launchctl to load the new settings:

    sudo launchctl load -w /Library/LaunchDaemons/limit.maxfiles.plist

You can confirm your hard maxfiles limit, before and after, as you noted:

    sysctl kern.maxfiles

And you can confirm your soft maxfiles limit, before and after, with:

    launchctl limit maxfiles

Reboot.

If needed, you can replace the 64000 value above with a larger number (of per user files).
