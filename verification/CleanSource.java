import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.WhileStmt;
import com.github.javaparser.printer.PrettyPrinter;
import com.github.javaparser.printer.configuration.PrettyPrinterConfiguration;
import java.nio.file.Files;
import java.nio.file.Path;

public class CleanSource {
    public static void main(String[] args) throws Exception {
        StaticJavaParser.getParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.BLEEDING_EDGE);
        PrettyPrinterConfiguration configuration = new PrettyPrinterConfiguration()
            .setIndentSize(4).setEndOfLineCharacter("\n").setOrderImports(true)
            .setColumnAlignParameters(false).setMaxEnumConstantsToAlignHorizontally(1);
        PrettyPrinter printer = new PrettyPrinter(configuration);
        int files = 0;
        try (var paths = Files.walk(Path.of(args[0]))) {
            for (Path path : paths.filter(file -> file.toString().endsWith(".java")).sorted().toList()) {
                CompilationUnit unit = StaticJavaParser.parse(path);
                unit.findAll(IfStmt.class).forEach(statement -> {
                    statement.setThenStmt(block(statement.getThenStmt()));
                    statement.getElseStmt().filter(branch -> !branch.isIfStmt())
                        .ifPresent(branch -> statement.setElseStmt(block(branch)));
                });
                unit.findAll(ForEachStmt.class).forEach(statement -> statement.setBody(block(statement.getBody())));
                unit.findAll(ForStmt.class).forEach(statement -> statement.setBody(block(statement.getBody())));
                unit.findAll(WhileStmt.class).forEach(statement -> statement.setBody(block(statement.getBody())));
                unit.findAll(DoStmt.class).forEach(statement -> statement.setBody(block(statement.getBody())));
                String clean = printer.print(unit);
                Files.writeString(path, clean);
                files++;
            }
        }
        System.out.println("Formatted " + files + " source files with explicit control-flow braces.");
    }

    private static Statement block(Statement statement) {
        return statement.isBlockStmt() ? statement : new BlockStmt().addStatement(statement.clone());
    }
}
