jmp MainCode

MainCode:
; TAC: x = 2
load R1, 2
store R1, [x]
; TAC: y = 3
load R1, 3
store R1, [y]
; TAC: z = 4
load R1, 4
store R1, [z]
; TAC: a = 14
load R1, 14
store R1, [a]
halt

; --- SECAO DE DADOS ---
a: db 0
x: db 0
y: db 0
z: db 0
