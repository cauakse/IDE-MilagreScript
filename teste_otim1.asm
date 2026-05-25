jmp MainCode

MainCode:
; TAC: a = 8
load R1, 8
store R1, [a]
; TAC: b = 20
load R1, 20
store R1, [b]
; TAC: c = 25
load R1, 25
store R1, [c]
; TAC: d = 13
load R1, 13
store R1, [d]
; TAC: e = 14
load R1, 14
store R1, [e]
halt

; --- SECAO DE DADOS ---
a: db 0
b: db 0
c: db 0
d: db 0
e: db 0
