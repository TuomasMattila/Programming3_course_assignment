# Programming3_course_assignment
Repository for the course assignment of the course Programming 3.

Tuomas Veikka Mattila 2587521 tuomas.mattila@windowslive.com

Explanation of the advanced feature implemented:

Feature: User can post messages to multiple chat channels

User can create a new channel by making a POST request to the /channels -realm.
The channel's name must be in JSON format, for example:
{
    "channel name" : "New Channel"
}

User can select the channel to post a message
If the users want to POST a message into a specific channel, the JSON containing
the message information must include the name of the channel, like so:
{
	"user": "username",
	"message": "This is an example message.",
	"sent": "2021-12-21T07:57:47.123Z",
	"channel": "New Channel"
}
if there is no channel specified in the JSON, the message will be posted 
to a "default" channel.

User can request a message from specific channel
If a users want to GET messages from a specific channel, they need to inlcude
a "Channel" -header in the request headers. For example, if using curl:
curl -k -u "username:password" https://localhost:8001/chat -H "Content-Type: application/json" -H "Channel: New Channel".
If there is no "Channel" -header, the user will be given the messages from the
"default" -channel

User can request a list of existing channels
Users can get a list of existing channels by making a GET request to the
/channels -realm.
