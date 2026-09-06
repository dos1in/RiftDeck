/* Original test program: display yellow and cyan bands in GBA mode 3. */
.syntax unified
.cpu arm7tdmi
.arm
.section .text
.global _start
_start:
    b entry
    .org 0xa0
    .ascii "RIFTDECK QA "
    .org 0xb2
    .byte 0x96
    .org 0xc0
entry:
    ldr r0, =0x04000000
    ldr r1, =0x0403
    strh r1, [r0]
    ldr r0, =0x06000000
    ldr r1, =0x03ff
    ldr r2, =19200
fill_yellow:
    strh r1, [r0], #2
    subs r2, r2, #1
    bne fill_yellow
    ldr r1, =0x7fe0
    ldr r2, =19200
fill_cyan:
    strh r1, [r0], #2
    subs r2, r2, #1
    bne fill_cyan
wait:
    b wait
    .ltorg
    /* GBA.emu rejects images smaller than its minimum ROM size. */
    .org 0x100000, 0xff
