jmp MainCode

MainCode:
; TAC: caua = 10
load R1, 10
store R1, [caua]
; TAC: cauaS = a
load R1, [a]
store R1, [cauaS]
; TAC: L1:
L1:
; TAC: if caua > 10 goto L2
load R1, [caua]
load R2, 10
load R3, -1
addi R0, R1, R3
jmpLE R2<=R0, L2
; TAC: goto L3
jmp L3
; TAC: L2:
L2:
; TAC: cauaS = a
load R1, [a]
store R1, [cauaS]
; TAC: goto L1
jmp L1
; TAC: L3:
L3:
; TAC: if cauaS != 10 goto L4
load R1, [cauaS]
load R2, 10
move R0, R2
jmpEQ R1=R0, Skip_1
jmp L4
Skip_1:
; TAC: goto L5
jmp L5
; TAC: L4:
L4:
; TAC: d = 10
load R1, 10
store R1, [d]
; TAC: L5:
L5:
; TAC: d = 20
load R1, 20
store R1, [d]
halt

; --- SECAO DE DADOS ---
caua: db 0
cauaS: db 0
d: db 0
a: db 0
