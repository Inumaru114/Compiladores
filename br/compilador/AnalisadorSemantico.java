package br.compilador;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

public class AnalisadorSemantico {

    // Tipos primitivos validos da linguagem LA
    private static final Set<String> TIPOS_PRIMITIVOS = new HashSet<>(Arrays.asList(
            "inteiro", "real", "literal", "logico", "ponteiro"
    ));

    // Palavras reservadas (nao sao identificadores de usuario)
    private static final Set<String> PALAVRAS_RESERVADAS = new HashSet<>(Arrays.asList(
            "se", "entao", "senao", "fim_se",
            "enquanto", "faca", "fim_enquanto",
            "para", "fim_para", "repita", "ate",
            "algoritmo", "fim_algoritmo",
            "declare", "leia", "escreva",
            "procedimento", "funcao", "fim_procedimento", "fim_funcao", "retorne",
            "tipo", "registro", "fim_registro",
            "e", "ou", "nao", "mod", "div",
            "verdadeiro", "falso",
            "inteiro", "real", "literal", "logico", "ponteiro"
    ));

    // Operadores relacionais — expressao que os contenha resulta em logico
    private static final Pattern OPERADOR_RELACIONAL =
            Pattern.compile("[<>]=?|<>|(?<![<>])=(?!=)");

    // Tabela de simbolos: identificador -> tipo
    private final Map<String, String> tabelaSimbolos = new LinkedHashMap<>();

    // Erros coletados: [numLinha (1-based), mensagem]
    private final List<int[]> errosIdx = new ArrayList<>();   // [linha, indice]
    private final List<String> mensagens = new ArrayList<>();
    private final Set<String> mensagensSet = new HashSet<>();

    // -----------------------------------------------------------------------
    // ENTRY POINT
    // -----------------------------------------------------------------------
    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("Uso: java -jar compilador.jar <entrada> <saida>");
            System.exit(1);
        }
        // Tenta UTF-8 primeiro; se falhar, usa ISO-8859-1 (Windows)
        List<String> linhas;
        try {
            linhas = Files.readAllLines(Paths.get(args[0]), StandardCharsets.UTF_8);
        } catch (Exception e) {
            linhas = Files.readAllLines(Paths.get(args[0]),
                    java.nio.charset.Charset.forName("ISO-8859-1"));
        }

        AnalisadorSemantico a = new AnalisadorSemantico();
        a.analisar(linhas);

        StringBuilder sb = new StringBuilder();
        for (String e : a.getErros()) sb.append(e).append("\n");
        sb.append("Fim da compilacao\n");

        Files.write(Paths.get(args[1]),
                sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    // -----------------------------------------------------------------------
    // ANALISE PRINCIPAL
    // -----------------------------------------------------------------------
    public void analisar(List<String> linhas) {
        tabelaSimbolos.clear();
        errosIdx.clear();
        mensagens.clear();
        mensagensSet.clear();

        // Remove comentarios { ... } de cada linha
        String[] sem = new String[linhas.size()];
        for (int i = 0; i < linhas.size(); i++) {
            sem[i] = linhas.get(i).replaceAll("\\{[^}]*}", "");
        }

        passagemDeclaracoes(sem);
        passagemUsos(sem);
    }

    // -----------------------------------------------------------------------
    // PASSAGEM 1: DECLARACOES
    // Formato: declare <id1>, <id2>, ... : <tipo>
    // A lista de ids pode se estender por multiplas linhas antes do ":"
    // -----------------------------------------------------------------------
    private void passagemDeclaracoes(String[] sem) {
        int i = 0;
        while (i < sem.length) {
            String linha = sem[i].trim();
            if (linha.toLowerCase().startsWith("declare")) {
                String resto = linha.substring("declare".length()).trim();
                int linhaInicio = i;

                // Acumula linhas ate encontrar ":"
                StringBuilder acumulado = new StringBuilder(resto);
                int linhaFim = i;

                while (!acumulado.toString().contains(":")) {
                    linhaFim++;
                    if (linhaFim >= sem.length) { linhaFim--; break; }
                    String prox = sem[linhaFim].trim();
                    if (iniciaComPalavraChave(prox)) { linhaFim--; break; }
                    acumulado.append(" ").append(prox);
                }

                processarBlocoDeclaracao(acumulado.toString(), linhaInicio, linhaFim, sem);
                i = linhaFim + 1;
            } else {
                i++;
            }
        }
    }

    private boolean iniciaComPalavraChave(String linha) {
        if (linha.isEmpty()) return false;
        String first = linha.split("\\s+")[0].toLowerCase();
        return PALAVRAS_RESERVADAS.contains(first);
    }

    /**
     * Processa "ids : tipo" ja concatenado.
     */
    private void processarBlocoDeclaracao(String bloco, int linhaInicio, int linhaFim, String[] sem) {
        int posColon = bloco.indexOf(':');
        if (posColon < 0) return;

        String listaIds = bloco.substring(0, posColon).trim();
        String tipoRaw  = bloco.substring(posColon + 1).trim();
        if (tipoRaw.isEmpty()) return;
        String tipo = tipoRaw.split("[\\s;,]")[0].toLowerCase();

        if (!TIPOS_PRIMITIVOS.contains(tipo)) {
            int linhaTipo = acharLinhaTipo(tipo, sem, linhaInicio, linhaFim);
            adicionarErro(linhaTipo + 1, "tipo " + tipo + " nao declarado");
            for (String id : listaIds.split(",")) {
                String nome = id.trim();
                if (!nome.isEmpty() && !PALAVRAS_RESERVADAS.contains(nome.toLowerCase())) {
                    if (!tabelaSimbolos.containsKey(nome)) tabelaSimbolos.put(nome, "indefinido");
                }
            }
            return;
        }

        for (String id : listaIds.split(",")) {
            String nome = id.trim();
            if (nome.isEmpty() || PALAVRAS_RESERVADAS.contains(nome.toLowerCase())) continue;

            if (tabelaSimbolos.containsKey(nome)) {
                int linhaDup = acharLinhaDuplicata(nome, sem, linhaInicio, linhaFim);
                adicionarErro(linhaDup + 1, "identificador " + nome + " ja declarado anteriormente");
            } else {
                tabelaSimbolos.put(nome, tipo);
            }
        }
    }

    /** Linha (0-based) onde o tipo aparece apos ":" no bloco. */
    private int acharLinhaTipo(String tipo, String[] sem, int inicio, int fim) {
        for (int k = inicio; k <= fim; k++) {
            String l = sem[k];
            if (l.contains(":")) {
                String dep = l.substring(l.indexOf(':') + 1).trim().toLowerCase();
                if (dep.startsWith(tipo)) return k;
            }
        }
        return fim;
    }

    /**
     * Linha (0-based) onde o nome aparece como DUPLICATA no bloco declare.
     *
     * Regras:
     *  - Se o token aparece 2+ vezes no bloco atual: a duplicata e a SEGUNDA ocorrencia.
     *    (ex: "declare a, b, a: real" -> a segunda "a" e a duplicata)
     *  - Se o token aparece apenas 1 vez no bloco atual: ele duplica um bloco anterior,
     *    entao essa unica ocorrencia no bloco atual e a duplicata.
     */
    private int acharLinhaDuplicata(String nome, String[] sem, int inicio, int fim) {
        int primeira = -1;
        int ocorrencias = 0;
        for (int k = inicio; k <= fim; k++) {
            String l = sem[k];
            String parteIds = l.contains(":") ? l.substring(0, l.indexOf(':')) : l;
            Matcher m = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_]*").matcher(parteIds);
            while (m.find()) {
                if (m.group().equals(nome)) {
                    ocorrencias++;
                    if (ocorrencias == 1) primeira = k;
                    if (ocorrencias >= 2) return k; // segunda ocorrencia = duplicata no mesmo bloco
                }
            }
        }
        // Apenas 1 ocorrencia no bloco atual = duplicata de bloco anterior
        return primeira >= 0 ? primeira : inicio;
    }

    // -----------------------------------------------------------------------
    // PASSAGEM 2: USOS
    // -----------------------------------------------------------------------
    private void passagemUsos(String[] sem) {
        int i = 0;
        while (i < sem.length) {
            String linha = sem[i].trim();
            if (linha.isEmpty()) { i++; continue; }

            String lower = linha.toLowerCase();

            if (lower.startsWith("declare")) { i++; continue; }

            // leia(...) — pode ter args em linhas seguintes
            if (lower.matches("leia\\s*\\(.*")) {
                int[] range = acharFimParenteses(sem, i);
                String acumulado = juntarLinhas(sem, i, range[0]);
                verificarLeia(acumulado, i + 1);
                i = range[0] + 1;
                continue;
            }

            // escreva(...) — pode ter args em linhas seguintes
            if (lower.matches("escreva\\s*\\(.*")) {
                int[] range = acharFimParenteses(sem, i);
                verificarEscrevaMultiline(sem, i, range[0]);
                i = range[0] + 1;
                continue;
            }

            // Atribuicao: id <- expr
            if (linha.contains("<-")) {
                verificarAtribuicao(linha, i + 1);
            }
            // enquanto <cond> faca
            else if (lower.startsWith("enquanto")) {
                String cond = extrairEntre(linha, "enquanto", "faca");
                verificarIdsExpressao(cond, i + 1);
            }
            // se <cond> entao
            else if (lower.startsWith("se ") && lower.contains("entao")) {
                String cond = extrairEntre(linha, "se", "entao");
                verificarIdsExpressao(cond, i + 1);
            }

            i++;
        }
    }

    /**
     * Retorna [linhaFim] onde os parenteses abertos em linhaInicio fecham.
     */
    private int[] acharFimParenteses(String[] sem, int inicio) {
        int nivel = 0;
        boolean emStr = false;
        for (int i = inicio; i < sem.length; i++) {
            for (char c : sem[i].toCharArray()) {
                if (c == '"') emStr = !emStr;
                if (!emStr) {
                    if (c == '(') nivel++;
                    else if (c == ')') { nivel--; if (nivel == 0) return new int[]{i}; }
                }
            }
        }
        return new int[]{inicio};
    }

    private String juntarLinhas(String[] sem, int inicio, int fim) {
        StringBuilder sb = new StringBuilder();
        for (int k = inicio; k <= fim; k++) {
            if (k > inicio) sb.append(" ");
            sb.append(sem[k].trim());
        }
        return sb.toString();
    }

    // -----------------------------------------------------------------------
    // leia(id1, id2, ...)
    // -----------------------------------------------------------------------
    private void verificarLeia(String linha, int numLinha) {
        String interior = extrairInteriorParenteses(linha);
        if (interior == null) return;
        for (String tok : interior.split(",")) {
            String id = tok.trim();
            if (!id.isEmpty() && !tabelaSimbolos.containsKey(id)) {
                adicionarErro(numLinha, "identificador " + id + " nao declarado");
            }
        }
    }

    // -----------------------------------------------------------------------
    // escreva(...) — versao multiline com numero de linha correto por token
    // -----------------------------------------------------------------------
    private void verificarEscrevaMultiline(String[] sem, int idxInicio, int idxFim) {
        // Junta tudo para separar os argumentos
        String acumulado = juntarLinhas(sem, idxInicio, idxFim);
        String interior = extrairInteriorParenteses(acumulado);
        if (interior == null) return;

        List<String> args = separarArgumentos(interior);
        for (String arg : args) {
            String a = arg.trim();
            if (a.isEmpty()) continue;
            if (a.startsWith("\"") || a.startsWith("\u201c")) continue;
            if (a.matches("-?\\d+(\\.\\d+)?")) continue;

            // Para cada identificador no argumento, acha a linha onde ele esta
            for (String[] tok : tokenizar(a)) {
                if ("ID".equals(tok[1])) {
                    String nome = tok[0];
                    if (!tabelaSimbolos.containsKey(nome)) {
                        int numLinha = acharLinhaToken(nome, sem, idxInicio, idxFim);
                        adicionarErro(numLinha, "identificador " + nome + " nao declarado");
                    }
                }
            }
        }
    }

    /** Numero de linha (1-based) onde o token aparece no intervalo. */
    private int acharLinhaToken(String token, String[] sem, int inicio, int fim) {
        Pattern p = Pattern.compile("\\b" + Pattern.quote(token) + "\\b");
        for (int k = inicio; k <= fim; k++) {
            if (p.matcher(sem[k]).find()) return k + 1;
        }
        return inicio + 1;
    }

    // -----------------------------------------------------------------------
    // Atribuicao: lhs <- rhs
    // -----------------------------------------------------------------------
    private void verificarAtribuicao(String linha, int numLinha) {
        int pos = linha.indexOf("<-");
        String lhs = linha.substring(0, pos).trim();
        String rhs = linha.substring(pos + 2).trim();

        if (!tabelaSimbolos.containsKey(lhs)) {
            adicionarErro(numLinha, "identificador " + lhs + " nao declarado");
            return;
        }
        String tipoLhs = tabelaSimbolos.get(lhs);
        if ("indefinido".equals(tipoLhs)) return;

        String tipoRhs = inferirTipo(rhs, numLinha);

        if (!tiposCompativeis(tipoLhs, tipoRhs)) {
            adicionarErro(numLinha, "atribuicao nao compativel para " + lhs);
        }
    }

    // -----------------------------------------------------------------------
    // Verifica identificadores nao declarados em uma expressao
    // -----------------------------------------------------------------------
    private void verificarIdsExpressao(String expr, int numLinha) {
        if (expr == null || expr.isEmpty()) return;
        for (String[] tok : tokenizar(expr)) {
            if ("ID".equals(tok[1]) && !tabelaSimbolos.containsKey(tok[0])) {
                adicionarErro(numLinha, "identificador " + tok[0] + " nao declarado");
            }
        }
    }

    // -----------------------------------------------------------------------
    // Inferencia de tipo de uma expressao RHS
    // -----------------------------------------------------------------------
    private String inferirTipo(String expr, int numLinha) {
        expr = expr.trim();

        // String literal
        if (expr.startsWith("\"") || expr.startsWith("\u201c") || expr.startsWith("\u201d"))
            return "literal";
        // Real
        if (expr.matches("-?\\d+\\.\\d+.*")) return "real";
        // Inteiro
        if (expr.matches("-?\\d+")) return "inteiro";
        // Booleano sem aspas
        if (expr.equalsIgnoreCase("verdadeiro") || expr.equalsIgnoreCase("falso"))
            return "logico";
        // Identificador simples
        if (expr.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
            if (PALAVRAS_RESERVADAS.contains(expr.toLowerCase())) return "indefinido";
            if (!tabelaSimbolos.containsKey(expr)) {
                adicionarErro(numLinha, "identificador " + expr + " nao declarado");
                return "indefinido";
            }
            return tabelaSimbolos.get(expr);
        }
        // Expressao composta
        return inferirTipoComposto(expr, numLinha);
    }

    private String inferirTipoComposto(String expr, int numLinha) {
        // Verifica se ha operadores relacionais ou logicos -> tipo logico
        boolean temRelacional = OPERADOR_RELACIONAL.matcher(expr).find();
        boolean temLogico = Pattern.compile("\\b(e|ou|nao)\\b",
                Pattern.CASE_INSENSITIVE).matcher(expr).find();

        List<String[]> tokens = tokenizar(expr);
        Set<String> tipos = new LinkedHashSet<>();
        boolean temIndefinido = false;

        for (String[] tok : tokens) {
            switch (tok[1]) {
                case "ID":
                    if (!tabelaSimbolos.containsKey(tok[0])) {
                        adicionarErro(numLinha, "identificador " + tok[0] + " nao declarado");
                        temIndefinido = true;
                    } else {
                        tipos.add(tabelaSimbolos.get(tok[0]));
                    }
                    break;
                case "INT":  tipos.add("inteiro"); break;
                case "REAL": tipos.add("real");    break;
                case "STR":  tipos.add("literal"); break;
                case "BOOL": tipos.add("logico");  break;
            }
        }

        if (temIndefinido) return "indefinido";

        // Expressao relacional/logica -> retorna logico
        if (temRelacional || temLogico) return "logico";

        if (tipos.isEmpty()) return "indefinido";
        if (tipos.size() == 1) return tipos.iterator().next();

        // (real | inteiro) misturados -> real
        boolean todosNum = true;
        for (String t : tipos) if (!t.equals("real") && !t.equals("inteiro")) { todosNum = false; break; }
        if (todosNum) return tipos.contains("real") ? "real" : "inteiro";

        return "indefinido";
    }

    // -----------------------------------------------------------------------
    // Tokenizador de expressao
    // Retorna lista de [valor, tipo] onde tipo: ID, INT, REAL, STR, BOOL
    // -----------------------------------------------------------------------
    private List<String[]> tokenizar(String expr) {
        List<String[]> tokens = new ArrayList<>();
        int i = 0;
        while (i < expr.length()) {
            char c = expr.charAt(i);

            if (Character.isWhitespace(c)) { i++; continue; }

            // String literal
            if (c == '"' || c == '\u201c' || c == '\u201d') {
                char fechar = (c == '\u201c') ? '\u201d' : '"';
                int j = i + 1;
                while (j < expr.length() && expr.charAt(j) != fechar) j++;
                tokens.add(new String[]{expr.substring(i, Math.min(j + 1, expr.length())), "STR"});
                i = j + 1;
                continue;
            }

            // Numero
            if (Character.isDigit(c)) {
                int j = i;
                boolean isReal = false;
                while (j < expr.length() && (Character.isDigit(expr.charAt(j)) || expr.charAt(j) == '.')) {
                    if (expr.charAt(j) == '.') isReal = true;
                    j++;
                }
                tokens.add(new String[]{expr.substring(i, j), isReal ? "REAL" : "INT"});
                i = j;
                continue;
            }

            // Identificador ou palavra reservada
            if (Character.isLetter(c) || c == '_') {
                int j = i;
                while (j < expr.length() && (Character.isLetterOrDigit(expr.charAt(j)) || expr.charAt(j) == '_')) j++;
                String word = expr.substring(i, j);
                String wl = word.toLowerCase();
                if (wl.equals("verdadeiro") || wl.equals("falso")) {
                    tokens.add(new String[]{word, "BOOL"});
                } else if (!PALAVRAS_RESERVADAS.contains(wl)) {
                    tokens.add(new String[]{word, "ID"});
                }
                i = j;
                continue;
            }
            i++;
        }
        return tokens;
    }

    // -----------------------------------------------------------------------
    // Compatibilidade de tipos
    // -----------------------------------------------------------------------
    private boolean tiposCompativeis(String lhs, String rhs) {
        if ("indefinido".equals(lhs) || "indefinido".equals(rhs)) return false;
        if (lhs.equals(rhs)) return true;
        boolean lNum = "real".equals(lhs) || "inteiro".equals(lhs);
        boolean rNum = "real".equals(rhs) || "inteiro".equals(rhs);
        return lNum && rNum;
    }

    // -----------------------------------------------------------------------
    // Utilitarios
    // -----------------------------------------------------------------------
    private String extrairEntre(String linha, String inicio, String fim) {
        String lower = linha.toLowerCase();
        int i = lower.indexOf(inicio.toLowerCase());
        if (i < 0) return "";
        i += inicio.length();
        int f = lower.lastIndexOf(fim.toLowerCase());
        if (f < i) f = linha.length();
        return linha.substring(i, f).trim();
    }

    private String extrairInteriorParenteses(String linha) {
        int abre  = linha.indexOf('(');
        int fecha = linha.lastIndexOf(')');
        if (abre < 0 || fecha <= abre) return null;
        return linha.substring(abre + 1, fecha);
    }

    private List<String> separarArgumentos(String interior) {
        List<String> result = new ArrayList<>();
        int nivel = 0;
        boolean emStr = false;
        StringBuilder atual = new StringBuilder();
        for (char c : interior.toCharArray()) {
            if (c == '"' || c == '\u201c' || c == '\u201d') emStr = !emStr;
            if (!emStr) {
                if (c == '(') nivel++;
                else if (c == ')') nivel--;
                else if (c == ',' && nivel == 0) {
                    result.add(atual.toString());
                    atual.setLength(0);
                    continue;
                }
            }
            atual.append(c);
        }
        if (atual.length() > 0) result.add(atual.toString());
        return result;
    }

    private void adicionarErro(int numLinha, String mensagem) {
        String chave = "Linha " + numLinha + ": " + mensagem;
        if (mensagensSet.add(chave)) {
            mensagens.add(chave);
            errosIdx.add(new int[]{numLinha, mensagens.size() - 1});
        }
    }

    public List<String> getErros() {
        errosIdx.sort(Comparator.comparingInt(a -> a[0]));
        List<String> resultado = new ArrayList<>();
        for (int[] par : errosIdx) resultado.add(mensagens.get(par[1]));
        return resultado;
    }

    public Map<String, String> getTabelaSimbolos() {
        return Collections.unmodifiableMap(tabelaSimbolos);
    }
}