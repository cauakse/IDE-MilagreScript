jmp MainCode

MainCode:
; TAC: x = 10
load R1, 10
store R1, [x]
; TAC: y = 20
load R1, 20
store R1, [y]
; TAC: soma = 30
load R1, 30
store R1, [soma]
; TAC: diff = -10
load R1, -10
store R1, [diff]
; TAC: prod = 200
load R1, 200
store R1, [prod]
halt

; --- SECAO DE DADOS ---
x: db 0
y: db 0
soma: db 0
diff: db 0
prod: db 0
