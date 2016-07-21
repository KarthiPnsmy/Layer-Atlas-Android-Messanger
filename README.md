#Atlas Messenger

This repo is originally forked form **https://github.com/layerhq/Atlas-Android-Messenger**. I have added support for send and receive audio message from the mobile app.


##Sending Audio Message:

**com.layer.atlas.AtlasMessageComposer** handles below things,

    * Recording audio message in .mp4
    * Saving in SD card
    * Displaying reording timer
    * Send message via Layer client

##Receiving Audio Message:

**com.layer.atlas.messagetypes.audio.AudioCellFactory** (which is new CellFactory to haldle audio message) handles below things,

    * Rendering MediaPlayer with Seekbar in MessageListActivity if mime type meets audio type
    * Downloading audio in SD card
    * Playing audio via MediaPlayer
    * Handles Paly/Pause

##Notes:

    * Reason for using .mp4 format is, FireFox not supporting .3gp files
    * This modified app will create below folder structure in SD card root,
      sd_card_root/Chat/Audio/Sent


##Disclaimer:

I haven't added **Runtime Permission** for API level 23. So this app will crash if you run on **API level >= 23**. So feel free to add runtime Permission and give me a PR. 

##Screens:

![Scrren 1](https://github.com/railskarthi/Layer-Atlas-Android-Messanger/blob/master/Screens/Screenshot_1.png)
