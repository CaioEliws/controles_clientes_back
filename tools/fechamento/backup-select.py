from pathlib import Path
p=Path('tools/fechamento/backup-ui.mjs');s=p.read_text(encoding='utf-8').replace("getByPlaceholder('Novo perfil')", "getByLabel('Nome do perfil',{exact:true})").replace("getByRole('combobox',{name:'Perfil ativo'})", "getByLabel('Selecionar perfil',{exact:true})");p.write_text(s,encoding='utf-8')
