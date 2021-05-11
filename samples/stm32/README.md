# 			Intel RealSense ID Samples for STM32
This folder contains basic code samples for STM32.
Note: There may be some difference between the STM32 devices/system. Please the double check the configuration in the code.

## Prerequisites (Hardware)

1. connect Intel RealSense ID F455/F450 to the UART3 on STM32 via host uart. (Figure 8-2. UART Interposer Reference Design in Intel RealSense ID F455/F450 Datasheet, https://www.intelrealsense.com/download/14251/)
host uart on F455/F450 is Pin#47 CPU_TXD2 Pin#49 CPU_RXD2.

1. connect the F455/F450 to Power using USB cable.

2. connect UART1 on STM32 to PC via USB2TTL dongle. This connection is used for STDIN/STDOUT. Please using UART utility software (for example PuTTY) connect to this UART, and than run the RSID sample on stm32 system.


## Prerequisites (Software)

STM32Cube MX
IDE (for example Keil MDK)

1. open rsid.ioc using STM32Cube MX, and click "GENERATE CODE" button.
2. open the project file (MDK-ARM/rsid.uvprojx) in this cases
3. build and flash the program to the device.