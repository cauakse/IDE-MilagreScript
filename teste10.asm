jmp MainCode

MainCode:
; TAC: a = 10
load R1, 10
store R1, [a]
halt

; --- SECAO DE DADOS ---
a: db 0
