# FS
# Compilation and Run
Compilation utilizes regular old javac
To run, supply the intended image file as the first argument and only argument on the command line.

# Implementation
To navigate the FAT and the data section of the disk image, BufferedInputStream is used heavily to enable skipping to desired targets.