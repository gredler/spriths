@echo off

cd target/graphs

del *.png

for /f %%a IN ('dir /b *.dot') do call dot.exe -Tpng -Gsize=14,14 -o%%a.small.png %%a

for /f %%a IN ('dir /b *.dot') do call dot.exe -Tpng -Gsize=40,40 -o%%a.big.png %%a

cd ../..
