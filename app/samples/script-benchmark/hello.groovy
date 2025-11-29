/* Basic script to find factorial */
def factorial(i, n) {
    if (i == 1) return n
    n *= i
    i-=1
    return factorial(i, n)
}
factorial(170,1) // just call
true
