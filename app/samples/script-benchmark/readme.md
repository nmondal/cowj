# How to GRAAL 

## Command Line 

See the file `graal.sh`.

## GraalPy 

1. Download appropriate `graalpy` binary ( community ) for the Cowj version
from here https://github.com/oracle/graalpython/releases 

2. Download and use the  `graalpy-*/bin/graalpy` to create an environment 
inside the `lib` folder of the project like this:

```shell 
graalpy -m venv ./lib/py 
```
This would install all the required tools into the virtual environment.

3. Now, activate the environment as:

```shell 
source ./lib/py/bin/activate 
```

4. At this point we are ready to install any package into the `lib/py` virtual env:

```shell
pip install requests
```

It would install the `requests` package.
Now it can be used by the Cowj system.
See the file `rea_demo.py3` in the current folder.
