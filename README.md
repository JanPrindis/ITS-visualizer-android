# ITS Visualizer for Android

ITS Visualizer is an native Android application designed to parse and visualize messages from Intelligent Transport Systems (ITS). It contains user-friendly interface designed for mobile phones, tablets and Android Auto. The application shows information about surrounding vehicles, infrastructure, hazards and more.

Application is intended to be used with a server, that will provide the captured ITS data via TCP socket.

It currently supports following ITS messages as defined by the ETSI standard:
- Cooperative Awareness Messages (CAM)
- Decentralized Environmental Notification Messages (DENM)
- Map Data Extended Message (MAPEM)
- Signal Phase and Timing Messages (SPATEM)
- Signal Request Extended Message (SREM)
- Signal Request Status Extended Message (SSEM)

## Screenshot
![Demo of CAM visualization](https://i.imgur.com/obfZGyC.png)

## General Requirements
- Android device running at least API 24 (Android 7.0)
- Router capable of capturing IEEE 802.11p
- Mapbox access token
- TShark (tested with TShark 4.0.10)

## Installation
To build and run the application using Android Studio yourself you will need to provide few things:

### Mapbox
This application uses Mapbox as the map layer provider and part of visualization process. You will need to register and obtain access tokens from [Mapbox website](mapbox.com). The service allows free tier for 100 monthly active users and 200,000 monthly trips (as of 2/2024).

After registration process, you will need to generate public and secret tokens. The secret token must have **_all public scopes_** checked and **_DOWNLOADS:READ_** checked.

![Secret token requirements](https://i.imgur.com/k6azZPa.png)

Once you have both public and secret tokens, replace the placeholder in **_gradle.properties_** with your secret token, and also replace the placeholder in **_app/src/main/res/values/developer-config.xml_** with your public token.

### Server requirements
This application requires an external server. You will need a router capable of capturing traffic from IEEE 802.11p. The captured traffic needs to be passed in a JSON fomat using TShark via TCP socket (tested with TShark 4.0.10). Different versions of TShark might use different JSON formatting, so the parser might not recognize the data.

### Application setup
Once you have the application installed and server running. You will need to input the servers IP address and port the first time you run the application in the settings. After that the application will automatically attempt to connect to server and await data.

## Testing
Application was tested on following devices:
- Pixel 7 _(Virtualized)_ on API 33 (Android 13.0)
- Xiaomi Mi 9T on API 30 (Android 11.0)
- Pixel Tablet _(Virtualized)_ on API 28 (Android 9.0)
- Huawei P9 Lite on API 24 (Android 7.0)

## Sources
The message parser was based on [VehicleVisualization from Milan Křivánek](https://github.com/krivmi/VehicleVisualization).

Included graphics and drawables are made by me, or royalty-free icons from [flaticons.net](https://flaticons.net/).

[Soud effect used](https://freesound.org/people/Tissman/sounds/521848/) is licenced under Creative Commons 0.

## License
This project is licensed under the GNU General Public License. See the LICENSE file for details.
