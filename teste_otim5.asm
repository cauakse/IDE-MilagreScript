jmp MainCode

MainCode:
; TAC: x = 10
load R1, 10
store R1, [x]
; TAC: y = 5
load R1, 5
store R1, [y]
; TAC: resultado = 15
load R1, 15
store R1, [resultado]
; TAC: total = 50
load R1, 50
store R1, [total]
halt

; --- SECAO DE DADOS ---
x: db 0
y: db 0
resultado: db 0
total: db 0
