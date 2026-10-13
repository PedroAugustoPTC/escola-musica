package br.com.felipeblaft.migration.rewrite;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

/**
 * Renomeia, pelo mapa de identificadores da fatia, os símbolos DECLARADOS no núcleo e todas as referências a eles:
 * métodos (inclusive os que sobrescrevem um método do núcleo em outro módulo), campos, parâmetros, variáveis locais e
 * componentes de {@code record}. O OpenRewrite não tem receita declarativa para estes casos (spike da fatia 2).
 *
 * <p>Só renomeia o que a análise de tipos resolve para um dono do núcleo (pacotes em {@code corePackages});
 * símbolos de bibliotecas e de outros módulos ficam como estão. Os membros listados em {@code held} (constantes de
 * enumeração e componentes de {@code record} lidos pelo Jackson, B1 e B2) nunca são renomeados. Literais e
 * comentários não são tocados.
 *
 * <p>Formato do arquivo de mapa (JSON): {@code corePackages}, {@code names}, {@code methodNames},
 * {@code variableNames} e {@code held} (caminho do tipo, sem pacote, para os nomes retidos).
 */
public class RenameMembersByMap extends Recipe {

    /**
     * O caminho do mapa vem da propriedade de sistema {@code rename.mapFile} ({@code mvn -Drename.mapFile=…}): a
     * ligação de opções da receita via YAML chegou nula neste plugin, e a receita não precisa de opções.
     */
    static final String MAP_FILE_PROPERTY = "rename.mapFile";

    @Override
    public String getDisplayName() {
        return "Renomear membros pelo mapa de identificadores";
    }

    @Override
    public String getDescription() {
        return "Renomeia métodos, campos, parâmetros, variáveis e componentes de record declarados no núcleo, "
                + "e as referências a eles, segundo o mapa de identificadores da fatia.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        String mapFile = System.getProperty(MAP_FILE_PROPERTY);
        if (mapFile == null) {
            throw new IllegalStateException("Informe -D" + MAP_FILE_PROPERTY + "=<arquivo do mapa>");
        }
        return new Renamer(Config.load(mapFile));
    }

    /** Configuração lida do JSON. */
    static final class Config {
        final List<String> corePackages;
        final Map<String, String> names = new HashMap<>();
        final Map<String, String> methodNames = new HashMap<>();
        final Map<String, String> variableNames = new HashMap<>();
        final Map<String, String> qualifiedMethods = new HashMap<>();
        final Map<String, Set<String>> held = new HashMap<>();

        private Config(JsonNode root) {
            corePackages = new java.util.ArrayList<>();
            root.get("corePackages").forEach(node -> corePackages.add(node.asText()));
            root.get("names").fields().forEachRemaining(entry -> names.put(entry.getKey(), entry.getValue().asText()));
            root.get("methodNames")
                    .fields()
                    .forEachRemaining(entry -> methodNames.put(entry.getKey(), entry.getValue().asText()));
            root.get("variableNames")
                    .fields()
                    .forEachRemaining(entry -> variableNames.put(entry.getKey(), entry.getValue().asText()));
            root.get("qualifiedMethods")
                    .fields()
                    .forEachRemaining(entry -> qualifiedMethods.put(entry.getKey(), entry.getValue().asText()));
            root.get("held").fields().forEachRemaining(entry -> {
                Set<String> members = new HashSet<>();
                entry.getValue().forEach(node -> members.add(node.asText()));
                held.put(entry.getKey(), members);
            });
        }

        static Config load(String path) {
            try {
                return new Config(new ObjectMapper().readTree(new File(path)));
            } catch (IOException e) {
                throw new UncheckedIOException("Não foi possível ler o mapa " + path, e);
            }
        }

        @Nullable String method(String name) {
            return methodNames.getOrDefault(name, names.get(name));
        }

        @Nullable String variable(String name) {
            return variableNames.getOrDefault(name, names.get(name));
        }
    }

    static final class Renamer extends JavaIsoVisitor<ExecutionContext> {
        private final Config config;

        Renamer(Config config) {
            this.config = config;
        }

        // ---- propriedade: o que pertence ao núcleo ------------------------------------------------------------

        private boolean isCore(JavaType.@Nullable FullyQualified type) {
            if (type == null) {
                return false;
            }
            String name = type.getFullyQualifiedName();
            for (String prefix : config.corePackages) {
                if (name.startsWith(prefix + ".")) {
                    return true;
                }
            }
            return false;
        }

        /** Caminho do tipo sem o pacote: {@code Externo.Aninhado}. */
        private static String pathOf(JavaType.FullyQualified type) {
            String pack = type.getPackageName();
            String name = type.getFullyQualifiedName();
            return (pack.isEmpty() ? name : name.substring(pack.length() + 1)).replace('$', '.');
        }

        private boolean isHeld(JavaType.@Nullable FullyQualified owner, String member) {
            if (owner == null) {
                return false;
            }
            Set<String> members = config.held.get(pathOf(owner));
            return members != null && members.contains(member);
        }

        /** Algum supertipo (em qualquer nível) é do núcleo e declara método de mesmo nome e aridade. */
        private boolean overridesCoreMethod(JavaType.@Nullable Method method) {
            return method != null
                    && superHasCoreMethod(
                            method.getDeclaringType(), method.getName(), method.getParameterTypes().size(), new HashSet<>());
        }

        private boolean superHasCoreMethod(
                JavaType.@Nullable FullyQualified type, String name, int arity, Set<String> seen) {
            if (type == null || !seen.add(type.getFullyQualifiedName())) {
                return false;
            }
            List<JavaType.FullyQualified> parents = new java.util.ArrayList<>(type.getInterfaces());
            if (type.getSupertype() != null) {
                parents.add(type.getSupertype());
            }
            for (JavaType.FullyQualified parent : parents) {
                if (isCore(parent)
                        && parent.getMethods().stream()
                                .anyMatch(m -> m.getName().equals(name) && m.getParameterTypes().size() == arity)) {
                    return true;
                }
                if (superHasCoreMethod(parent, name, arity, seen)) {
                    return true;
                }
            }
            return false;
        }

        /** Método do núcleo, ou que sobrescreve (em qualquer nível) um método do núcleo. */
        private boolean methodOwnedByCore(JavaType.@Nullable Method method) {
            if (overridesCoreMethod(method)) {
                return true;
            }
            JavaType.Method current = method;
            for (int depth = 0; current != null && depth < 16; depth++) {
                if (isCore(current.getDeclaringType())) {
                    return true;
                }
                current = TypeUtils.findOverriddenMethod(current).orElse(null);
            }
            return false;
        }

        /** Nome novo do método: a chave qualificada (`TipoAntigo#nome`) vence o mapa geral. */
        private @Nullable String methodTarget(JavaType.@Nullable Method method, String name) {
            JavaType.Method current = method;
            for (int depth = 0; current != null && depth < 16; depth++) {
                if (current.getDeclaringType() != null) {
                    String qualified = config.qualifiedMethods.get(simpleNameOf(current.getDeclaringType()) + "#" + name);
                    if (qualified != null) {
                        return qualified;
                    }
                }
                current = TypeUtils.findOverriddenMethod(current).orElse(null);
            }
            return config.method(name);
        }

        private boolean methodHeld(JavaType.@Nullable Method method, String name) {
            JavaType.Method current = method;
            for (int depth = 0; current != null && depth < 16; depth++) {
                if (isHeld(current.getDeclaringType(), name)) {
                    return true;
                }
                current = TypeUtils.findOverriddenMethod(current).orElse(null);
            }
            return false;
        }

        /** Dono do símbolo: a classe (campo) ou a classe que declara o método (parâmetro, variável local). */
        private static JavaType.@Nullable FullyQualified classOwnerOf(JavaType.@Nullable Variable variable) {
            if (variable == null) {
                return null;
            }
            JavaType owner = variable.getOwner();
            if (owner instanceof JavaType.Method method) {
                return method.getDeclaringType();
            }
            return TypeUtils.asFullyQualified(owner);
        }

        // ---- métodos --------------------------------------------------------------------------------------------

        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration declaration, ExecutionContext ctx) {
            J.MethodDeclaration visited = super.visitMethodDeclaration(declaration, ctx);
            if (visited.isConstructor()) {
                // construtor (inclusive o compacto de record) de tipo do núcleo acompanha o nome do tipo
                J.ClassDeclaration enclosing = getCursor().firstEnclosing(J.ClassDeclaration.class);
                String renamed = config.names.get(visited.getSimpleName());
                if (enclosing != null
                        && renamed != null
                        && isCoreType(enclosing.getType())
                        && enclosing.getSimpleName().equals(visited.getSimpleName())) {
                    return visited.withName(visited.getName().withSimpleName(renamed));
                }
                return visited;
            }
            JavaType.Method type = visited.getMethodType();
            String target = methodTarget(type, visited.getSimpleName());
            if (type == null || target == null || target.equals(visited.getSimpleName())) {
                return visited;
            }
            if (!methodOwnedByCore(type) || methodHeld(type, visited.getSimpleName())) {
                return visited;
            }
            return visited.withName(visited.getName().withSimpleName(target)).withMethodType(type.withName(target));
        }

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation, ExecutionContext ctx) {
            J.MethodInvocation visited = super.visitMethodInvocation(invocation, ctx);
            JavaType.Method type = visited.getMethodType();
            String target = methodTarget(type, visited.getSimpleName());
            if (type == null || target == null || target.equals(visited.getSimpleName())) {
                return visited;
            }
            if (!methodOwnedByCore(type) || methodHeld(type, visited.getSimpleName())) {
                return visited;
            }
            return visited.withName(visited.getName().withSimpleName(target)).withMethodType(type.withName(target));
        }

        @Override
        public J.MemberReference visitMemberReference(J.MemberReference reference, ExecutionContext ctx) {
            J.MemberReference visited = super.visitMemberReference(reference, ctx);
            String current = visited.getReference().getSimpleName();
            JavaType.Method type = visited.getMethodType();
            JavaType.FullyQualified containing = TypeUtils.asFullyQualified(visited.getContaining().getType());
            String target = type != null
                    ? methodTarget(type, current)
                    : config.qualifiedMethods.getOrDefault(
                            (containing == null ? "" : simpleNameOf(containing)) + "#" + current, config.method(current));
            if (target == null || target.equals(current)) {
                return visited;
            }
            boolean owned = type != null
                    ? methodOwnedByCore(type) && !methodHeld(type, current)
                    : isCoreType(containing) && !isHeld(containing, current);
            if (!owned) {
                return visited;
            }
            J.MemberReference renamed = visited.withReference(visited.getReference().withSimpleName(target));
            return type == null ? renamed : renamed.withMethodType(type.withName(target));
        }

        /**
         * No construtor compacto de um record do núcleo os componentes são parâmetros implícitos: não há declaração
         * nem tipo de variável, só o nome. Renomeia a referência se o nome é de um componente (não retido) do record.
         */
        private @Nullable String compactConstructorParameter(J.Identifier original, J.Identifier visited) {
            J.MethodDeclaration method = getCursor().firstEnclosing(J.MethodDeclaration.class);
            J.ClassDeclaration record = getCursor().firstEnclosing(J.ClassDeclaration.class);
            if (method == null || record == null || !method.isConstructor()
                    || record.getKind() != J.ClassDeclaration.Kind.Type.Record || !isCoreType(record.getType())
                    || !(method.getParameters().isEmpty() || method.getParameters().get(0) instanceof J.Empty)) {
                return null;
            }
            Object parent = getCursor().getParentTreeCursor().getValue();
            if ((parent instanceof J.FieldAccess access && access.getName() == original)
                    || (parent instanceof J.MethodInvocation call && call.getName() == original)
                    || (parent instanceof J.MemberReference ref && ref.getReference() == original)) {
                return null;
            }
            String target = config.variable(visited.getSimpleName());
            if (target == null || target.equals(visited.getSimpleName()) || isHeld(record.getType(), visited.getSimpleName())) {
                return null;
            }
            for (var statement : record.getPrimaryConstructor()) {
                if (statement instanceof J.VariableDeclarations declarations) {
                    for (J.VariableDeclarations.NamedVariable component : declarations.getVariables()) {
                        if (component.getSimpleName().equals(visited.getSimpleName())) {
                            return target;
                        }
                    }
                }
            }
            return null;
        }

        // ---- campos, parâmetros, variáveis locais e componentes de record ---------------------------------------

        private boolean variableOwnedByCore(JavaType.@Nullable Variable variable) {
            return isCore(classOwnerOf(variable));
        }

        // ---- tipos (o ChangeType do OpenRewrite não serve: qualifica referências a tipo aninhado, quebra o construtor
        //      compacto de record e não reescreve o import de tipo aninhado quando só o externo é renomeado) --------

        private static String simpleNameOf(JavaType.FullyQualified type) {
            String name = type.getFullyQualifiedName();
            return name.substring(Math.max(name.lastIndexOf('.'), name.lastIndexOf('$')) + 1);
        }

        private boolean isCoreType(JavaType.@Nullable FullyQualified type) {
            return type != null && isCore(type);
        }

        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration declaration, ExecutionContext ctx) {
            J.ClassDeclaration visited = super.visitClassDeclaration(declaration, ctx);
            String target = config.names.get(visited.getSimpleName());
            if (target == null || !isCoreType(visited.getType())) {
                return visited;
            }
            return visited.withName(visited.getName().withSimpleName(target));
        }

        @Override
        public J.FieldAccess visitFieldAccess(J.FieldAccess access, ExecutionContext ctx) {
            J.FieldAccess visited = super.visitFieldAccess(access, ctx);
            JavaType.FullyQualified type = TypeUtils.asFullyQualified(visited.getType());
            if (isCoreType(type) && visited.getSimpleName().equals(simpleNameOf(type))) {
                String target = config.names.get(visited.getSimpleName());
                if (target != null && !target.equals(visited.getSimpleName())) {
                    return visited.withName(visited.getName().withSimpleName(target));
                }
            }
            return visited;
        }

        @Override
        public J.CompilationUnit visitCompilationUnit(J.CompilationUnit unit, ExecutionContext ctx) {
            J.CompilationUnit visited = super.visitCompilationUnit(unit, ctx);
            String fileName = unit.getSourcePath().getFileName().toString();
            for (J.ClassDeclaration original : unit.getClasses()) {
                String target = config.names.get(original.getSimpleName());
                if (target != null && fileName.equals(original.getSimpleName() + ".java") && isCoreType(original.getType())) {
                    return visited.withSourcePath(unit.getSourcePath().resolveSibling(target + ".java"));
                }
            }
            return visited;
        }

        @Override
        public J.Identifier visitIdentifier(J.Identifier identifier, ExecutionContext ctx) {
            J.Identifier visited = super.visitIdentifier(identifier, ctx);
            JavaType.Variable fieldType = visited.getFieldType();
            if (fieldType == null) {
                String implicit = compactConstructorParameter(identifier, visited);
                if (implicit != null) {
                    return visited.withSimpleName(implicit);
                }
                // referência a tipo do núcleo: renomeia o nome simples (o tipo fica como está nesta passada)
                JavaType.FullyQualified type = TypeUtils.asFullyQualified(visited.getType());
                if (isCoreType(type) && visited.getSimpleName().equals(simpleNameOf(type))) {
                    String target = config.names.get(visited.getSimpleName());
                    if (target != null && !target.equals(visited.getSimpleName())) {
                        return visited.withSimpleName(target);
                    }
                }
                return visited;
            }
            String target = config.variable(visited.getSimpleName());
            if (target == null || target.equals(visited.getSimpleName())) {
                return visited;
            }
            if (!variableOwnedByCore(fieldType) || isHeld(classOwnerOf(fieldType), visited.getSimpleName())) {
                return visited;
            }
            return visited.withSimpleName(target).withFieldType(fieldType.withName(target));
        }

        @Override
        public J.VariableDeclarations.NamedVariable visitVariable(
                J.VariableDeclarations.NamedVariable variable, ExecutionContext ctx) {
            J.VariableDeclarations.NamedVariable visited = super.visitVariable(variable, ctx);
            JavaType.Variable type = visited.getVariableType();
            String current = visited.getSimpleName();
            if (type != null && !type.getName().equals(current)) {
                // o nome já foi renomeado pelo identificador; o tipo da variável acompanha
                return visited.withVariableType(type.withName(current));
            }
            String target = config.variable(current);
            if (type == null || target == null || target.equals(current)) {
                return visited;
            }
            if (!variableOwnedByCore(type) || isHeld(classOwnerOf(type), current)) {
                return visited;
            }
            return visited.withName(visited.getName().withSimpleName(target)).withVariableType(type.withName(target));
        }

        // ---- import static ----------------------------------------------------------------------------------------

        @Override
        public J.Import visitImport(J.Import anImport, ExecutionContext ctx) {
            J.Import visited = super.visitImport(anImport, ctx);
            if (!visited.isStatic()) {
                return visited;
            }
            String member = visited.getQualid().getSimpleName();
            if ("*".equals(member)) {
                return visited;
            }
            JavaType.FullyQualified owner = TypeUtils.asFullyQualified(visited.getQualid().getTarget().getType());
            if (!isCore(owner) || isHeld(owner, member)) {
                return visited;
            }
            boolean isMethod = owner.getMethods().stream().anyMatch(method -> method.getName().equals(member));
            String target = isMethod ? config.method(member) : config.variable(member);
            if (target == null || target.equals(member)) {
                return visited;
            }
            return visited.withQualid(visited.getQualid().withName(visited.getQualid().getName().withSimpleName(target)));
        }
    }
}
