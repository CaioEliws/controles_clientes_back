SELECT current_database() AS banco, inet_server_port() AS porta, version();
SELECT c.conname AS constraint_name, c.conrelid::regclass AS tabela,
       c.confrelid::regclass AS referencia, pg_get_constraintdef(c.oid) AS definicao,
       c.confdeltype AS delete_rule
FROM pg_constraint c JOIN pg_namespace n ON n.oid=c.connamespace
WHERE c.contype='f' AND n.nspname='public' ORDER BY c.conrelid::regclass::text,c.conname;
SELECT table_name,column_name,data_type,is_nullable
FROM information_schema.columns WHERE table_schema='public'
AND (table_name='contratos_gerados' OR data_type='bytea') ORDER BY table_name,ordinal_position;
SELECT conname,conrelid::regclass AS tabela,pg_get_constraintdef(oid) AS definicao
FROM pg_constraint WHERE connamespace='public'::regnamespace AND contype IN ('u','p','c') ORDER BY conname;
SELECT schemaname,tablename,indexname,indexdef FROM pg_indexes WHERE schemaname='public' ORDER BY tablename,indexname;
SELECT 'emprestimos_sem_cliente' AS verificacao,count(*) FROM emprestimos e LEFT JOIN clientes c ON c.id=e.cliente_id WHERE c.id IS NULL
UNION ALL SELECT 'parcelas_sem_emprestimo',count(*) FROM parcelas p LEFT JOIN emprestimos e ON e.id=p.emprestimo_id WHERE e.id IS NULL
UNION ALL SELECT 'contratos_sem_emprestimo',count(*) FROM contratos_gerados c LEFT JOIN emprestimos e ON e.id=c.emprestimo_id WHERE e.id IS NULL
UNION ALL SELECT 'observacoes_sem_parcela',count(*) FROM parcela_observacoes o LEFT JOIN parcelas p ON p.id=o.parcela_id WHERE p.id IS NULL;
SELECT count(*) AS objetos_large_object_separados FROM pg_largeobject_metadata;
