-- Unify log table names under flow_log_ prefix
RENAME TABLE flow_login_log TO flow_log_login;
RENAME TABLE flow_execution_log TO flow_log_execution;
RENAME TABLE flow_task_log TO flow_log_task;
