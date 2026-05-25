jmp MainCode

MainCode:
; TAC: caua = 5
load R1, 5
store R1, [caua]
; TAC: L1:
L1:
; TAC: if caua > 1 goto L2
load R1, [caua]
load R2, 1
load R3, -1
addi R0, R1, R3
jmpLE R2<=R0, L2
; TAC: goto L3
jmp L3
; TAC: L2:
L2:
; TAC: dois = "1231"
load R1, [str_1231]
store R1, [dois]
; TAC: t1 = caua / 3
load R1, [caua]
load R2, 3
load R3, 0
load R4, 1
load R6, -1
xor R5, R2, R6
addi R5, R5, R4
DivLoop_1:
addi R0, R2, R6
jmpLE R1<=R0, DivEnd_1
addi R1, R1, R5
addi R3, R3, R4
jmp DivLoop_1
DivEnd_1:
store R3, [t1]
; TAC: if t1 > 5 goto L4
load R1, [t1]
load R2, 5
load R3, -1
addi R0, R1, R3
jmpLE R2<=R0, L4
; TAC: goto L5
jmp L5
; TAC: L4:
L4:
; TAC: dois = "doisabubeblebla"
load R1, [str_doisabubeblebla]
store R1, [dois]
; TAC: L5:
L5:
; TAC: t2 = caua - 1
load R1, [caua]
load R2, 1
load R4, -1
xor R2, R2, R4
load R4, 1
addi R2, R2, R4
addi R3, R1, R2
store R3, [t2]
; TAC: caua = t2
load R1, [t2]
store R1, [caua]
; TAC: goto L1
jmp L1
; TAC: L3:
L3:
halt

; --- SECAO DE DADOS ---
caua: db 0
dois: db 0
t1: db 0
t2: db 0
; --- LITERAIS DE STRING ---
; "str_1231" => ID 1
str_1231: db 1
; "str_doisabubeblebla" => ID 2
str_doisabubeblebla: db 2
