jmp MainCode

MainCode:
; TAC: s1 = "hello"
load R1, [str_hello]
store R1, [s1]
; TAC: s2 = "world"
load R1, [str_world]
store R1, [s2]
; TAC: t1 = s1 + s2
load R1, [s1]
load R2, [s2]
addi R3, R1, R2
store R3, [t1]
; TAC: s3 = t1
load R1, [t1]
store R1, [s3]
halt

; --- SECAO DE DADOS ---
s1: db 0
s2: db 0
s3: db 0
t1: db 0
; --- LITERAIS DE STRING ---
; "str_hello" => ID 1
str_hello: db 1
; "str_world" => ID 2
str_world: db 2
