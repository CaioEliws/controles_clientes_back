from pathlib import Path
root=Path('src/main/java/com/caio/controle_clientes')
for p in (root/'services').rglob('*.java'):
 s=p.read_text(encoding='utf-8')
 if 'new RuntimeException(' not in s: continue
 s=s.replace('new RuntimeException(', 'new RegraNegocioException(')
 if 'import com.caio.controle_clientes.exceptions.RegraNegocioException;' not in s:
  at=s.index('\n',s.index('package '))+1
  s=s[:at]+'\nimport com.caio.controle_clientes.exceptions.RegraNegocioException;\n'+s[at:]
 p.write_text(s,encoding='utf-8')
for name in ['EmprestimoQuitadoException','ParcelaPagaException']:
 p=root/'exceptions'/f'{name}.java';s=p.read_text(encoding='utf-8').replace('extends RuntimeException','extends RegraNegocioException');p.write_text(s,encoding='utf-8')
p=root/'exceptions/GlobalExceptionHandler.java';s=p.read_text(encoding='utf-8')
s=s.replace('public class GlobalExceptionHandler extends RuntimeException {','public class GlobalExceptionHandler {\n    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class);')
s=s.replace('        ex.printStackTrace();','        LOG.warn("Falha HTTP: {}", ex.getClass().getSimpleName());')
s=s.replace('@ExceptionHandler(RuntimeException.class)\n    public ResponseEntity<Map<String, Object>> handleRuntime(RuntimeException ex)', '@ExceptionHandler(RegraNegocioException.class)\n    public ResponseEntity<Map<String, Object>> handleRuntime(RegraNegocioException ex)')
p.write_text(s,encoding='utf-8')
