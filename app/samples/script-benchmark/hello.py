"""
Basic script to find factorial
"""
def factorial(i, num):
    if i == 1:
        return num
    num *= i
    i-=1
    return factorial(i, num)
pass

# else in python there is a problem
factorial(170,1)
_res = True
