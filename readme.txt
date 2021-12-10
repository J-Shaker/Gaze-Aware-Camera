To run this application, you will first need to download and install Android Studio. That can be found here: https://developer.android.com/studio.

The directory contains the entire project, including OpenCV, and is properly configured. There are two choices for running the application: through an emulator, or on a device. If you
wish to use an emulator, you can create one using AVD manager. If you choose to use an emulator, there is no particular device we recommend. However, know that you will have to alter
the configuration of the emulator to replace the virtual camera with your computer's webcam. You can do this by 

AVD manager -> right click your emulator -> edit -> show advanced settings -> camera

Depending on your computer's webcam, this may not work. When using webcams, the emulator often uses the incorrect orientation, which prevents the application from working effectively.

If you wish to use a device, the device must run Android 11 or later and developer options must be enabled on the device. Upon plugging the device into your computer, Android Studio
should recognize the device and allow you to install the application to it. The application will be permanently installed, and you can run it independently of Android Studio. If you
run it from within Android Studio, you can see the run terminal. Valuable information about what the application is doing is printed to the terminal and it is recommended that you
view it while testing the application in various environments.