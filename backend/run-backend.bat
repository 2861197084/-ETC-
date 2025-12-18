@echo off
cd /d %~dp0
echo Building project (skip tests)...
call mvnw.cmd -DskipTests package
if errorlevel 1 (
  echo Build failed.
  exit /b 1
)
echo Starting etc-backend jar...
java -jar target\etc-backend-0.0.1-SNAPSHOT.jar
