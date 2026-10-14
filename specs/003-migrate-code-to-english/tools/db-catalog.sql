-- Catálogo do esquema `public` como JSON (T058, R-03): tabelas, colunas, restrições, índices, sequências.
-- Roda com `psql -At -f`; a saída é um único documento JSON.
select json_build_object(
  'tables', (
    select json_agg(t order by t.name) from (
      select c.relname as name,
        (select json_agg(json_build_object(
            'name', a.attname,
            'type', format_type(a.atttypid, a.atttypmod),
            'notNull', a.attnotnull,
            'default', pg_get_expr(d.adbin, d.adrelid),
            'identity', a.attidentity,
            'generated', a.attgenerated) order by a.attnum)
           from pg_attribute a
           left join pg_attrdef d on d.adrelid = a.attrelid and d.adnum = a.attnum
          where a.attrelid = c.oid and a.attnum > 0 and not a.attisdropped) as columns
        from pg_class c
        join pg_namespace n on n.oid = c.relnamespace
       where n.nspname = 'public' and c.relkind = 'r' and c.relname <> 'flyway_schema_history'
    ) t),
  'constraints', (
    select json_agg(json_build_object(
        'table', c.conrelid::regclass::text,
        'name', c.conname,
        'type', c.contype,
        'definition', pg_get_constraintdef(c.oid)) order by c.conrelid::regclass::text, c.conname)
      from pg_constraint c
      join pg_namespace n on n.oid = c.connamespace
     where n.nspname = 'public' and c.conrelid::regclass::text <> 'flyway_schema_history'),
  'indexes', (
    select json_agg(json_build_object(
        'table', tablename, 'name', indexname, 'definition', indexdef) order by tablename, indexname)
      from pg_indexes
     where schemaname = 'public' and tablename <> 'flyway_schema_history'),
  'sequences', (
    select json_agg(json_build_object(
        'name', s.sequencename, 'dataType', s.data_type::text, 'start', s.start_value,
        'increment', s.increment_by, 'cycle', s.cycle,
        'ownedBy', (select d.refobjid::regclass::text || '.' || a.attname
                      from pg_depend d
                      join pg_attribute a on a.attrelid = d.refobjid and a.attnum = d.refobjsubid
                     where d.objid = (quote_ident(s.schemaname) || '.' || quote_ident(s.sequencename))::regclass
                       and d.deptype in ('a', 'i') limit 1)) order by s.sequencename)
      from pg_sequences s where s.schemaname = 'public')
);
