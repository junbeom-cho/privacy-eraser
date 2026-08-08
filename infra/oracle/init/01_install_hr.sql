-- HR 샘플 스키마를 FREEPDB1 에 설치합니다.
--
-- 공식 hr_install.sql 은 ACCEPT 로 비밀번호·테이블스페이스를 대화형으로 묻기 때문에
-- 컨테이너에서 무인 실행할 수 없습니다. 그래서 변수를 미리 DEFINE 하고
-- 사용자 생성 부분만 옮겨온 뒤, 실제 작업을 하는 하위 스크립트 3개를 그대로 부릅니다.
--
-- 이 프로젝트에서 HR 은 raw_schema(비식별화 대상 원본) 역할입니다.

DEFINE pass = hr_dev_2026
DEFINE tbs = USERS

SET ECHO OFF
SET FEEDBACK OFF
WHENEVER SQLERROR EXIT 1

-- 이미 있으면 지우고 다시 만듭니다 (재실행 가능하게).
DECLARE
   v_exists all_users.username%TYPE;
BEGIN
   SELECT MAX(username) INTO v_exists FROM all_users WHERE username = 'HR';
   IF v_exists IS NOT NULL THEN
      EXECUTE IMMEDIATE 'DROP USER HR CASCADE';
   END IF;
END;
/

CREATE USER hr IDENTIFIED BY "&pass"
               DEFAULT TABLESPACE &tbs
               QUOTA UNLIMITED ON &tbs;

GRANT CREATE MATERIALIZED VIEW,
      CREATE PROCEDURE,
      CREATE SEQUENCE,
      CREATE SESSION,
      CREATE SYNONYM,
      CREATE TABLE,
      CREATE TRIGGER,
      CREATE TYPE,
      CREATE VIEW
  TO hr;

ALTER SESSION SET CURRENT_SCHEMA=HR;
ALTER SESSION SET NLS_LANGUAGE=American;
ALTER SESSION SET NLS_TERRITORY=America;

@@/opt/oracle/sample/human_resources/hr_create.sql
@@/opt/oracle/sample/human_resources/hr_populate.sql
@@/opt/oracle/sample/human_resources/hr_code.sql

EXIT
