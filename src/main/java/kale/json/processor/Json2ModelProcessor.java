package kale.json.processor;

import com.jsonformat.JsonParserHelper;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

import kale.json.Json2Model;
import kale.json.constant.DefaultValue;

/**
 * @author Jack Tony
 * @date 2015/8/13
 */
@SupportedAnnotationTypes({"kale.json.Json2Model"})
@SupportedSourceVersion(SourceVersion.RELEASE_7)
public class Json2ModelProcessor extends AbstractProcessor {

    private static final String TAG = "[ " + Json2Model.class.getSimpleName() + " ]:";

    // 元素操作的辅助类  
    private Elements elementUtils;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        // 元素操作的辅助类  
        elementUtils = processingEnv.getElementUtils();
    }

    private String packageName;

    // 会被多次调用
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (TypeElement te : annotations) {
            // 返回所有被注解的列表
            for (Element e : roundEnv.getElementsAnnotatedWith(te)) {
                log("Working on: " + e.toString());
                VariableElement varE = (VariableElement) e;

                Json2Model json2Model = e.getAnnotation(Json2Model.class);
                if (DefaultValue.NULL.equals(json2Model.packageName())) {
                    /**
                     *  得到这个变量的值.
                     *  example:
                     *  String GET_USER_INFO = "create/info/user/info"; 
                     *  得到了：create/info/user/info
                     */
                    String url = varE.getConstantValue().toString();
                    // create/info/user/info -> create.info.user.info
                    packageName = url.replaceAll("/", ".");
                    if (packageName.substring(packageName.length() - 1).equals(".")) {
                        packageName = packageName.substring(0, packageName.length() - 1);
                    }
                } else {
                    packageName = json2Model.packageName();
                }
                
                if (json2Model.jsonStr() == null || json2Model.jsonStr().equals("")) {
                    fatalError("json string is null");
                }
                
                final String clsName = json2Model.modelName();

                JsonParserHelper helper = new JsonParserHelper();
                helper.parse(json2Model.jsonStr(), clsName, new JsonParserHelper.ParseListener() {
                    public void onParseComplete(String str) {
                        createModelClass(packageName, clsName, "package " + packageName + ";\n" + str);
                    }

                    public void onParseError(Exception e) {
                        e.printStackTrace();
                        fatalError(e.getMessage());
                    }
                });
                log("Complete on: " + e.toString());
            }
        }
        return true;
    }

    private void createModelClass(String packageName, String clsName, String content) {
        //PackageElement pkgElement = elementUtils.getPackageElement("");
        TypeElement pkgElement = elementUtils.getTypeElement(packageName);

        OutputStreamWriter osw = null;
        try {
            // 建立资源文件
            JavaFileObject fileObject = processingEnv.getFiler().createSourceFile(packageName + "." + clsName, pkgElement);
            OutputStream os = fileObject.openOutputStream();
            osw = new OutputStreamWriter(os, Charset.forName("UTF-8"));
            osw.write(content, 0, content.length());

        } catch (IOException e) {
            e.printStackTrace();
            fatalError(e.getMessage());
        } finally {
            try {
                if (osw != null) {
                    osw.flush();
                    osw.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
                fatalError(e.getMessage());
            }
        }
    }

    private void log(String msg) {
        if (processingEnv.getOptions().containsKey("debug")) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, TAG + msg);
        }
    }

    /*private void error(String msg, Element element, AnnotationMirror annotation) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, TAG + msg, element, annotation);
    }*/

    private void fatalError(String msg) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, TAG + " FATAL ERROR: " + msg);
    }

}
