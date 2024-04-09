# Sensor data sender
This is an Android application that collects sensor data at the moment of hitting an object and transmits it to a server.  

![knocking](https://github.com/gjlee0802/sensor-data-sender/assets/49184890/1a635dd9-21e6-45a0-b1df-414d8347fd0c)  
Frequency domain analysis is performed through FFT, and if the ratio of the sum of the high-frequency components and the sum of the low-frequency components exceeds the threshold, it is considered to have hit the object.  
If you shake a smartphone in the air like hitting an object, it is not recognized as hitting an object. Only data from the moment it hits an object are collected. Shaking in the air has more low-frequency components, so the ratio does not exceed the threshold.  
Before performing FFT, 32 data samples are subjected to zero padding to make 256 samples. The reason for this is to increase the frequency resolution.  

**This software is manufactured by gjlee0802@naver.com .**  
