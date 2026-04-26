# Analisador Semântico — Linguagem Algorítmica (LA)

Trabalho 3 (T3) da disciplina **Construção de Compiladores** — DC/UFSCar.

**Grupo:**
Yasmin Victoria Oliveira - RA: 812308
Anna Carolina Brito Santos Farias - RA: 811448
Vitor Yuki Inumaru Ferreira - RA: 794041

---

## Descrição

Implementação de um analisador semântico para a **Linguagem Algorítmica (LA)**, desenvolvida pelo prof. Jander. O analisador detecta erros semânticos em programas escritos na linguagem LA e produz um relatório de erros em arquivo de saída.

### Erros detectados

| # | Tipo de erro | Exemplo de mensagem |
|---|---|---|
| 1 | Identificador já declarado no mesmo escopo | `Linha 6: identificador troco ja declarado anteriormente` |
| 2 | Tipo não declarado | `Linha 7: tipo inteir nao declarado` |
| 3 | Identificador não declarado | `Linha 11: identificador idades nao declarado` |
| 4 | Atribuição incompatível com o tipo declarado | `Linha 13: atribuicao nao compativel para colidiu` |

### Regras de compatibilidade de tipos

| Atribuição | Permitida? |
|---|---|
| `(real \| inteiro) ← (real \| inteiro)` | ✓ |
| `literal ← literal` | ✓ |
| `logico ← logico` | ✓ |
| `ponteiro ← ponteiro` | ✓ |
| `registro ← registro` (mesmo tipo) | ✓ |
| Qualquer outro mix | ✗ — gera `tipo_indefinido` |

O analisador **não interrompe a execução ao encontrar um erro** — continua até o final do arquivo reportando todos os erros encontrados.

---

## Pré-requisitos

- **Java JDK 8 ou superior** (recomendado: JDK 11+)

Verifique se está instalado:
```bash
java -version
javac -version
```

A saída esperada deve ser similar a:
```
java version "11.0.2" 2019-01-15 LTS
javac 11.0.2
```

Caso não esteja instalado:
- **Windows:** Baixe em https://www.oracle.com/java/technologies/downloads/
- **Linux (Ubuntu/Debian):** `sudo apt install default-jdk`
- **macOS:** `brew install openjdk`

---

## Compilação

### Windows (PowerShell ou CMD)

```powershell
cd C:\Compi

mkdir br\compilador

copy AnalisadorSemantico.java br\compilador\AnalisadorSemantico.java

javac -encoding UTF-8 br\compilador\AnalisadorSemantico.java

"Main-Class: br.compilador.AnalisadorSemantico`n" | Out-File -Encoding ascii MANIFEST.MF

jar cfm AnalisadorSemantico.jar MANIFEST.MF br\
```

### Linux / macOS

```bash
cd ~/compilador

mkdir -p br/compilador

cp AnalisadorSemantico.java br/compilador/AnalisadorSemantico.java

javac -encoding UTF-8 br/compilador/AnalisadorSemantico.java

echo -e "Main-Class: br.compilador.AnalisadorSemantico\n" > MANIFEST.MF

jar cfm AnalisadorSemantico.jar MANIFEST.MF br/
```

---

## Execução

O analisador recebe **dois argumentos obrigatórios**:

```
java -jar AnalisadorSemantico.jar <arquivo_entrada> <arquivo_saida>
```

| Argumento | Descrição |
|---|---|
| `arquivo_entrada` | Caminho completo para o arquivo de código-fonte em LA |
| `arquivo_saida` | Caminho completo para o arquivo onde a saída será gravada |

### Exemplo — Windows

```powershell
java -jar C:\Compi\AnalisadorSemantico.jar C:\casos-de-teste\entrada\1.algoritmo_2-2_apostila_LA.txt C:\Temp\saida.txt
```

### Exemplo — Linux / macOS

```bash
java -jar /compilador/AnalisadorSemantico.jar /casos-de-teste/entrada/1.algoritmo_2-2_apostila_LA.txt /tmp/saida.txt
```

---

## Formato da saída

A saída é gravada no arquivo indicado. Cada erro ocupa uma linha, no formato:

```
Linha <N>: <mensagem do erro>
Fim da compilacao
```

### Exemplo

**Entrada:**
```
{ leitura de nome e idade }
algoritmo
    declare
        nome: literal
    declare
        idade: inteir
    leia(nome)
    leia(idades)
    escreva(nome, " tem ", idade, " anos.")
fim_algoritmo
```

**Saída:**
```
Linha 7: tipo inteir nao declarado
Linha 11: identificador idades nao declarado
Fim da compilacao
```

---

## Usando o corretor automático

```powershell
java -jar compiladores-corretor-automatico-1.0-SNAPSHOT-jar-with-dependencies.jar "java -jar C:\Compi\AnalisadorSemantico.jar" gcc C:\Temp C:\casos-de-teste "794041, 811448, 812308" t3
```

---

## Estrutura do projeto

```
.
├── AnalisadorSemantico.java   # Código-fonte principal
├── MANIFEST.MF                # Manifesto para geração do JAR
├── AnalisadorSemantico.jar    # Executável gerado após compilação
└── README.md                  # Esta documentação
```

---

## Descrição interna do código

O analisador opera em **duas passagens** sobre o código-fonte:

### Passagem 1 — Coleta de declarações
Percorre todas as linhas buscando blocos `declare`. Para cada bloco:
- Extrai a lista de identificadores e o tipo declarado
- Verifica se o tipo é primitivo válido (`inteiro`, `real`, `literal`, `logico`, `ponteiro`); caso contrário, reporta **tipo não declarado**
- Registra cada identificador na tabela de símbolos; caso já exista, reporta **identificador já declarado**

### Passagem 2 — Verificação de usos
Percorre todas as linhas verificando:
- **`leia(...)`** — cada argumento deve ser um identificador declarado
- **`escreva(...)`** — cada identificador referenciado deve estar declarado
- **`id <- expressao`** — o identificador do lado esquerdo deve estar declarado e o tipo da expressão deve ser compatível com o tipo declarado
- **`enquanto`/`se`** — identificadores usados na condição devem estar declarados

### Inferência de tipos em expressões
- Literais numéricos: `inteiro` ou `real`
- Strings entre aspas: `literal`
- `verdadeiro`/`falso`: `logico`
- Expressões com operadores relacionais (`<`, `>`, `<=`, `>=`, `=`, `<>`) ou lógicos (`e`, `ou`, `nao`): resultado é `logico`
- Mix incompatível (ex: `literal + inteiro`): resultado é `indefinido`, inviabilizando a atribuição
