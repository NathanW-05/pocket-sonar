**Pocket Sonar** -- A simple, unfinished sample for the purposes of recording and preserving my work.
This app functions just like a hand-held radar gun; except, instead of radio waves, it uses sound waves. It is designed to detect small thrown projectiles (e.g. baseballs).

**How it Works**
1. <u>Calibration</u> (WIP): The calibration process essentially just reads the azimuth normal to the ball's direction of velocity. Then, when the device is pointed at the pitcher, it can calculate an angle to account for doppler effect cosine errors.

2. <u>Base Tone Emission</u>: The app emits a constant tone of around 17khz (this value may be adjusted). This way, we have an expected range in which doppler shifted waves may be picked up. Operating with such high frequencies are essential to avoiding most (but not all) interference and background noise in the detection process.
  
3. <u>Throw Detection</u>: It is really challenging to only listen for signals caused by a baseball's doppler shift. The signal is just too quiet relative to noise; to detect it accuratley, the sensitivity must be super high, and this results in many false positives. I believe this is because the baseball is quite small (maybe absorptive?), making it hard to detect the few waves that bounce off it. A cool solution I thought up was to detect the players hand and arm moving through the air moments before the ball is in the air and detectable. Then, when the throw is detected, the program accepts doppler shifts in the range where a baseball is likely, but with higher sensitivity. Thus, the likelyhood of detecting a false baseball decreases as compounded by the likelyhood of detecting a false throw.

4. <u>Baseball Detection</u>: As I've alluded to, the baseball detection works by checking for distinct, high frequency signals that are likely caused by doppler shifted waves off the base frequency. Not much to be said here.

5. <u>Doppler Shift Calculations</u>: After finding a (hopefully) valid frequency shift, getting the baseball speed is easy. Below is the equation:

