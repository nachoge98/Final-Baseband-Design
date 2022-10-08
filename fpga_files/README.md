# FPGA-Configuration

#TERMINAL 1:
cd /home/ignacio/Desktop/thesis/riscv-openocd/
./src/openocd -f ../sifive_freedom/cfg_OpenOCD/olimex-arm-usb-tiny-h.cfg -f ../sifive_freedom/cfg_OpenOCD/myConfig.cfg

#TERMINAL 2:
cd /home/ignacio/Desktop/thesis/sifive_freedom/riscv64-unknown-elf-toolchain-10.2.0-2020.12.8-x86_64-linux-ubuntu14/bin
./riscv64-unknown-elf-gdb
(gdb): set remotetimeout unlimited
(gdb): target extended-remote localhost:3333
(gdb): file test.riscv
(gdb): load
(gdb): c #for continuing the program or step to execute "instruction"
