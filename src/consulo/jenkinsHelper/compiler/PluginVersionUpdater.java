package consulo.jenkinsHelper.compiler;

import java.io.DataInput;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.consulo.compiler.ModuleCompilerPathsManager;
import org.consulo.module.extension.ModuleExtension;
import org.consulo.module.extension.ModuleExtensionWithSdk;
import org.jdom.Document;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mustbe.consulo.roots.impl.ProductionContentFolderTypeProvider;
import org.mustbe.consulo.roots.impl.ProductionResourceContentFolderTypeProvider;
import com.intellij.compiler.impl.javaCompiler.JavaCompilerConfiguration;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.compiler.EmptyValidityState;
import com.intellij.openapi.compiler.PackagingCompiler;
import com.intellij.openapi.compiler.ValidityState;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkTable;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.uiDesigner.GuiDesignerConfiguration;
import consulo.lombok.annotations.Logger;

/**
 * @author VISTALL
 * @since 29.01.14
 */
@Logger
public class PluginVersionUpdater implements PackagingCompiler
{
	public static class MyItem implements ProcessingItem
	{
		private final VirtualFile myVirtualFile;
		private final boolean myApplicationInfo;

		public MyItem(VirtualFile virtualFile, boolean applicationInfo)
		{
			myVirtualFile = virtualFile;
			myApplicationInfo = applicationInfo;
		}

		@NotNull
		@Override
		public VirtualFile getFile()
		{
			return myVirtualFile;
		}

		@Nullable
		@Override
		public ValidityState getValidityState()
		{
			return new EmptyValidityState();
		}
	}

	private static final String META_INF_PLUGIN_XML = "META-INF/plugin.xml";

	private static List<String> ourBytecodeVersions = Arrays.asList("1.6", "1.8");
	private static final String BUILD_NUMBER = System.getProperty("cold.build.number");

	private final Project myProject;

	public PluginVersionUpdater(Project project)
	{
		myProject = project;
	}

	@NotNull
	@Override
	public ProcessingItem[] getProcessingItems(CompileContext compileContext)
	{
		if(!isConsuloOrganizationProject() || BUILD_NUMBER == null)
		{
			return ProcessingItem.EMPTY_ARRAY;
		}

		ProductionContentFolderTypeProvider srcType = ProductionContentFolderTypeProvider.getInstance();
		ProductionResourceContentFolderTypeProvider resType = ProductionResourceContentFolderTypeProvider.getInstance();

		List<ProcessingItem> list = new ArrayList<ProcessingItem>();
		ModuleManager moduleManager = ModuleManager.getInstance(compileContext.getProject());
		for(Module module : moduleManager.getModules())
		{
			VirtualFile compilerOutput = ModuleCompilerPathsManager.getInstance(module).getCompilerOutput(resType);

			if(compilerOutput != null)
			{
				VirtualFile fileByRelativePath = compilerOutput.findFileByRelativePath(META_INF_PLUGIN_XML);
				if(fileByRelativePath != null)
				{
					list.add(new MyItem(fileByRelativePath, false));
				}
			}

			compilerOutput = ModuleCompilerPathsManager.getInstance(module).getCompilerOutput(resType);

			if(compilerOutput != null)
			{
				VirtualFile fileByRelativePath = compilerOutput.findFileByRelativePath("idea/ConsuloApplicationInfo.xml");
				if(fileByRelativePath != null)
				{
					list.add(new MyItem(fileByRelativePath, true));
				}
			}

			compilerOutput = ModuleCompilerPathsManager.getInstance(module).getCompilerOutput(srcType);

			if(compilerOutput != null)
			{
				VirtualFile fileByRelativePath = compilerOutput.findFileByRelativePath(META_INF_PLUGIN_XML);
				if(fileByRelativePath != null)
				{
					list.add(new MyItem(fileByRelativePath, false));
				}
			}
		}
		return list.toArray(new ProcessingItem[list.size()]);
	}

	@Override
	public ProcessingItem[] process(CompileContext compileContext, ProcessingItem[] processingItems)
	{
		if(!isConsuloOrganizationProject())
		{
			return ProcessingItem.EMPTY_ARRAY;
		}

		String consuloVersion = "SNAPSHOT";

		Sdk sdk = SdkTable.getInstance().findSdk("Consulo SNAPSHOT");
		if(sdk != null)
		{
			consuloVersion = sdk.getVersionString();
		}

		for(ProcessingItem processingItem : processingItems)
		{
			try
			{
				MyItem myItem = (MyItem) processingItem;

				VirtualFile file = myItem.getFile();
				byte[] bytes = file.contentsToByteArray();

				Document document = JDOMUtil.loadDocument(bytes);
				Element rootElement = document.getRootElement();

				if(myItem.myApplicationInfo)
				{
					if(!rootElement.getName().equals("component"))
					{
						continue;
					}

					final String date = new SimpleDateFormat("yyyyMMddHHmm").format(System.currentTimeMillis());
					Element ideaVersion = rootElement.getChild("build");
					if(ideaVersion != null)
					{
						ideaVersion.setAttribute("number", BUILD_NUMBER);
						ideaVersion.setAttribute("date", date);
					}
					else
					{
						rootElement.addContent(new Element("build").setAttribute("number", BUILD_NUMBER).setAttribute("date", date));
					}
				}
				else
				{
					if(!rootElement.getName().equals("idea-plugin"))
					{
						continue;
					}

					Element version = rootElement.getChild("version");
					if(version != null)
					{
						version.setText(BUILD_NUMBER);
					}
					else
					{
						rootElement.addContent(new Element("version").setText(BUILD_NUMBER));
					}

					Element platformVersion = rootElement.getChild("platformVersion");
					if(platformVersion != null)
					{
						platformVersion.setText(consuloVersion);
					}
					else
					{
						rootElement.addContent(new Element("platformVersion").setText(consuloVersion));
					}
				}

				file.setBinaryContent(JDOMUtil.printDocument(document, "\n"));
			}
			catch(Exception e)
			{
				LOGGER.warn(e);
			}
		}
		return new ProcessingItem[0];
	}

	@NotNull
	@Override
	public String getDescription()
	{
		return "plugin.xml updater";
	}

	@Override
	public boolean validateConfiguration(CompileScope compileScope)
	{
		if(!isConsuloOrganizationProject())
		{
			return true;
		}

		Module[] affectedModules = compileScope.getAffectedModules();
		for(Module affectedModule : affectedModules)
		{
			ModuleExtension<?> extension = ModuleUtilCore.getExtension(affectedModule, "consulo-plugin");
			if(extension instanceof ModuleExtensionWithSdk)
			{
				String sdkName = ((ModuleExtensionWithSdk) extension).getSdkName();
				if("Consulo 1.SNAPSHOT".equals(sdkName))
				{
					LOGGER.error("ConsuloDevKit: 'Consulo 1.SNAPSHOT' is obsolete and not used, migrate to 'Consulo SNAPSHOT' instead");
					return false;
				}
			}
		}

		JavaCompilerConfiguration javaCompilerConfiguration = JavaCompilerConfiguration.getInstance(myProject);
		if(!ourBytecodeVersions.contains(javaCompilerConfiguration.getProjectBytecodeTarget()))
		{
			LOGGER.error("Java: Bytecode target is not specified or wrong. Need " + ourBytecodeVersions + "");
			return false;
		}

		if(!javaCompilerConfiguration.isAddNotNullAssertions())
		{
			LOGGER.error("Java: '@NotNull' asserting is disabled. Enable it");
			return false;
		}

		GuiDesignerConfiguration guiDesignerConfiguration = GuiDesignerConfiguration.getInstance(myProject);
		if(guiDesignerConfiguration.COPY_FORMS_RUNTIME_TO_OUTPUT)
		{
			LOGGER.error("UI Designer: 'Copy forms runtime to output' is enabled. Disable it");
			return false;
		}

		if(!guiDesignerConfiguration.COPY_FORMS_TO_OUTPUT)
		{
			LOGGER.error("UI Designer: 'Copy forms to output' is disabled. Enable it");
			return false;
		}
		return true;
	}

	@Override
	public void init(@NotNull CompilerManager compilerManager)
	{

	}

	@Override
	public ValidityState createValidityState(DataInput dataInput) throws IOException
	{
		return new EmptyValidityState();
	}

	private boolean isConsuloOrganizationProject()
	{
		return myProject.getName().startsWith("consulo");
	}

	@Override
	public void processOutdatedItem(CompileContext compileContext, String s, @Nullable ValidityState validityState)
	{

	}
}
