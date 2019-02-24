#!/bin/bash
port=${1:-8080}
count=0
token=eyJraWQiOiJsTm9qcUQ4OEZRcHBNVkFHM2traExNQ1NYMFB3R2FoVndPWUR1cXl6MUt3IiwiYWxnIjoiUlMyNTYifQ.eyJ2ZXIiOjEsImp0aSI6IkFULnBQR3NtY0k5RmhrLXk4eGJoVG5WYlluTmdDbkprRUpLX1RCZUtkOGp3RmMiLCJpc3MiOiJodHRwczovL2Rldi03Mzc1MjMub2t0YXByZXZpZXcuY29tL29hdXRoMi9kZWZhdWx0IiwiYXVkIjoiYXBpOi8vZGVmYXVsdCIsImlhdCI6MTU1MDExNDIyOSwiZXhwIjoxNTUwMTE3ODI5LCJjaWQiOiIwb2FqMnBxYXR6NndWYXloazBoNyIsInVpZCI6IjAwdWZraWQ0b2ZIRU1uSjJFMGg3Iiwic2NwIjpbImVtYWlsIiwicHJvZmlsZSIsIm9wZW5pZCJdLCJzdWIiOiJtYXR0LnJhaWJsZUBva3RhLmNvbSIsImdyb3VwcyI6WyJFdmVyeW9uZSIsIlJPTEVfVVNFUiIsIlJPTEVfQURNSU4iXX0.58Pw4FS8rN28t4i2eV5g4T1hKb5mm0rTLW94raA3FRL7BKAH4itD0hB6qWzSYg-u3N_sxExdmt0v89xiDFT8bID6SP8JUoa2HAZ1Fstge0A_zIT9w4IGZascVjG5FOW3UEjxoLWf9dbql-DXjfgJTK580cAvzwq5YrE68eDymI3CnD06brL6kDC-g27MV5DklsD9PwtUBfQdNoLcvaYqsslsqAtyxWwRL339X2IUNyAAXmDc4UWPWxkJ7mjxUhNcS76TBYSJVc7Lgf-ecxi4iUg1RtQocfQowUDFgsMJe_YcXrxQdLVq_YLlSkW4j45tJgRgLwSccY2uLLMyqufp2Q

profile () {
  ((count++))
  echo "posting #${count}"
  http POST http://localhost:${port}/profiles email="random${count}" Authorization:"Bearer ${token}"
  if [ $count -gt 120 ]
  then
    echo "count is $count, ending..."
    break
  fi
}

while sleep 1; do profile; done
