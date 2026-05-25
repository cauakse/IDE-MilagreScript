jmp MainCode

MainCode:
; TAC: contador = 1
load R1, 1
store R1, [contador]
; TAC: limite = 4
load R1, 4
store R1, [limite]
; TAC: L1:
L1:
; TAC: if contador < 10 goto L2
load R1, [contador]
load R2, 10
load R3, -1
addi R0, R2, R3
jmpLE R1<=R0, L2
; TAC: goto L3
jmp L3
; TAC: L2:
L2:
; TAC: t1 = limite + 1
load R1, [limite]
load R2, 1
addi R3, R1, R2
store R3, [t1]
; TAC: x = t1
load R1, [t1]
store R1, [x]
; TAC: valor = 5.5
load R1, 5.5
store R1, [valor]
; TAC: t2 = (int) 5.5
load R1, 5.5
store R1, [t2]
; TAC: inteiro = t2
load R1, [t2]
store R1, [inteiro]
; TAC: temp = 1
load R1, 1
store R1, [temp]
; TAC: t3 = (int) 1
load R1, 1
store R1, [t3]
; TAC: t4 = t3 + 1
load R1, [t3]
load R2, 1
addi R3, R1, R2
store R3, [t4]
; TAC: contador = t4
load R1, [t4]
store R1, [contador]
; TAC: t5 = contador + 1
load R1, [contador]
load R2, 1
addi R3, R1, R2
store R3, [t5]
; TAC: contador = t5
load R1, [t5]
store R1, [contador]
; TAC: goto L1
jmp L1
; TAC: L3:
L3:
halt

; --- SECAO DE DADOS ---
contador: db 0
limite: db 0
x: db 0
valor: db 0
inteiro: db 0
temp: db 0
t1: db 0
t2: db 0
t3: db 0
t4: db 0
t5: db 0
