#!/usr/bin/expect

spawn scp  NettySpringWebServer.war ubuntu@115.159.154.160:~

expect "*password:"

send "ubuntu13859017\r"

interact
