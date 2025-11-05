/* Basic script to find factorial */

function factorial(i, num) {
    if (i == 1) return num;
    num *= i;
    i--;
    return factorial(i, num);
}

module.exports = factorial ;