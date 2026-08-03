-- Fix typo in SNOWFLAKE macro expression for existing MySQL database
UPDATE `flow_sys_macro`
SET `expression` = 'T(org.yu.flow.auto.util.SnowIdGenerator).getId()',
    `update_time` = NOW()
WHERE `macro_code` = 'SNOWFLAKE'
  AND `expression` LIKE '%util.auto.org.yu.flow%';
