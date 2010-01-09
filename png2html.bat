@echo off

SETLOCAL ENABLEDELAYEDEXPANSION

cd target/graphs

del graphs.html

echo ^<html^>^<head^>^<title^>Spriths Graphs^</title^>^</head^>^<body^>>> graphs.html
for /f %%a IN ('dir /b *.small.png') do (
  set b=%%a
  set b=!b:.small.=.big.!
  echo ^<a href='!b!'^>^<img style='border:1px solid black; padding: 5px;' src='%%a'^>^</a^>^<p^>>> graphs.html
)
echo ^</body^>^</html^>>> graphs.html

cd ../..
