jmp MainCode

MainCode:
; TAC: b = 2
load R1, 2
store R1, [b]
; TAC: c = 3
load R1, 3
store R1, [c]
; TAC: a = 5
load R1, 5
store R1, [a]
halt

; --- SECAO DE DADOS ---
a: db 0
b: db 0
c: db 0
