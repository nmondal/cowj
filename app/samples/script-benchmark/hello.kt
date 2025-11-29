/* Basic script to find factorial
*  https://discuss.kotlinlang.org/t/mutable-function-parameters/27041/3
*  */
fun factorial(i : Long, n: Long ) : Long {
    if ( i == 1L ) return n
    var n = n * i
    var i = i - 1
    return factorial(i, n)
}
factorial(170,1) // just call
true
