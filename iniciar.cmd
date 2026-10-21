@echo off
cd /d "%~dp0backend"
if exist mvnw.cmd (
  call mvnw.cmd -B spring-boot:run
) else (
  call mvn -B spring-boot:run
)
pause
