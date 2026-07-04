#!/usr/bin/expect -f
set timeout 60
cd /root/BabyCare
spawn git push origin master:main
expect "Enter passphrase for key"
send "ml000@ml\r"
expect eof