package org.mustbe.consulo.vulcanHelper.compiler;

import java.io.DataInput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.consulo.compiler.ModuleCompilerPathsManager;
import org.consulo.lombok.annotations.Logger;
import org.jdom.Document;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mustbe.consulo.roots.impl.ProductionContentFolderTypeProvider;
import org.mustbe.consulo.roots.impl.ProductionResourceContentFolderTypeProvider;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.compiler.EmptyValidityState;
import com.intellij.openapi.compiler.Validator;
import com.intellij.openapi.compiler.ValidityState;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * @author VISTALL
 * @since 29.01.14
 */
@Logger
public class PluginVersionUpdater implements Validator
{
	public static class MyItem implements ProcessingItem
	{
		private final VirtualFile myVirtualFile;

		public MyItem(VirtualFile virtualFile)
		{
			myVirtualFile = virtualFile;
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

	private static final String BUILD_NUMBER = System.getProperty("vulcan.build.number");

	@NotNull
	@Override
	public ProcessingItem[] getProcessingItems(CompileContext compileContext)
	{
		if(BUILD_NUMBER == null)
		{
			return ProcessingItem.EMPTY_ARRAY;
		}
		List<ProcessingItem> list = new ArrayList<ProcessingItem>();
		ModuleManager moduleManager = ModuleManager.getInstance(compileContext.getProject());
		for(Module module : moduleManager.getModules())
		{
			VirtualFile compilerOutput = ModuleCompilerPathsManager.getInstance(module).getCompilerOutput
					(ProductionResourceContentFolderTypeProvider.getInstance());

			if(compilerOutput != null)
			{
				VirtualFile fileByRelativePath = compilerOutput.findFileByRelativePath(META_INF_PLUGIN_XML);
				if(fileByRelativePath != null)
				{
					list.add(new MyItem(fileByRelativePath));
				}
			}

			compilerOutput = ModuleCompilerPathsManager.getInstance(module).getCompilerOutput(ProductionContentFolderTypeProvider.getInstance());

			if(compilerOutput != null)
			{
				VirtualFile fileByRelativePath = compilerOutput.findFileByRelativePath(META_INF_PLUGIN_XML);
				if(fileByRelativePath != null)
				{
					list.add(new MyItem(fileByRelativePath));
				}
			}
		}
		return list.toArray(new ProcessingItem[list.size()]);
	}

	@Override
	public ProcessingItem[] process(CompileContext compileContext, ProcessingItem[] processingItems)
	{
		for(ProcessingItem processingItem : processingItems)
		{
			try
			{
				VirtualFile file = processingItem.getFile();
				byte[] bytes = file.contentsToByteArray();

				Document document = JDOMUtil.loadDocument(bytes);
				Element rootElement = document.getRootElement();
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
}
