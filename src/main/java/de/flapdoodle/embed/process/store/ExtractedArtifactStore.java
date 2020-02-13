/**
 * Copyright (C) 2011
 *   Michael Mosmann <michael@mosmann.de>
 *   Martin Jöhren <m.joehren@googlemail.com>
 *
 * with contributions from
 * 	konstantin-ba@github,
	Archimedes Trajano (trajano@github),
	Kevin D. Keck (kdkeck@github),
	Ben McCann (benmccann@github)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.flapdoodle.embed.process.store;

import java.io.File;
import java.io.IOException;

import de.flapdoodle.embed.process.config.store.FileSet;
import de.flapdoodle.embed.process.config.store.FileType;
import de.flapdoodle.embed.process.config.store.IDownloadConfig;
import de.flapdoodle.embed.process.distribution.Distribution;
import de.flapdoodle.embed.process.extract.DirectoryAndExecutableNaming;
import de.flapdoodle.embed.process.extract.ExtractedFileSets;
import de.flapdoodle.embed.process.extract.FilesToExtract;
import de.flapdoodle.embed.process.extract.IExtractedFileSet;
import de.flapdoodle.embed.process.extract.ITempNaming;
import de.flapdoodle.embed.process.extract.ImmutableExtractedFileSet;
import de.flapdoodle.embed.process.extract.ImmutableExtractedFileSet.Builder;
import de.flapdoodle.embed.process.io.directories.IDirectory;
import de.flapdoodle.embed.process.io.file.FileAlreadyExistsException;

public class ExtractedArtifactStore implements IArtifactStore {

	private final IDownloadConfig downloadConfig;
	private final IDownloader downloader;
	private final DirectoryAndExecutableNaming extraction;
	private final DirectoryAndExecutableNaming temp;

	public ExtractedArtifactStore(IDownloadConfig downloadConfig, IDownloader downloader, DirectoryAndExecutableNaming extraction, DirectoryAndExecutableNaming temp) {
		this.downloadConfig = downloadConfig;
		this.downloader = downloader;
		this.extraction = extraction;
		this.temp = temp;
	}
	
	@Override
	public boolean checkDistribution(Distribution distribution)
			throws IOException {
		return store().checkDistribution(distribution);
	}

	private ArtifactStore store() {
		return new ArtifactStore(downloadConfig, extraction.getDirectory(), extraction.getExecutableNaming(), downloader);
	}

	private ArtifactStore store(IDirectory withDistribution, ITempNaming naming) {
		return new ArtifactStore(downloadConfig, withDistribution, naming, downloader);
	}


	@Override
	public IExtractedFileSet extractFileSet(Distribution distribution)
			throws IOException {
		
		IDirectory withDistribution = withDistribution(extraction.getDirectory(), distribution);
		ArtifactStore baseStore = store(withDistribution, extraction.getExecutableNaming());
		
		boolean foundExecutable=false;
		File destinationDir = withDistribution.asFile();
		
		Builder fileSetBuilder = ImmutableExtractedFileSet.builder(destinationDir)
				.baseDirIsGenerated(withDistribution.isGenerated());
		
		FilesToExtract filesToExtract = baseStore.filesToExtract(distribution);
		for (FileSet.Entry file : filesToExtract.files()) {
			if (file.type()==FileType.Executable) {
				String executableName = FilesToExtract.executableName(extraction.getExecutableNaming(), file);
				File executableFile = new File(executableName);
				File resolvedExecutableFile = new File(destinationDir, executableName);
				if (resolvedExecutableFile.isFile()) {
					foundExecutable=true;
				}
				fileSetBuilder.file(file.type(), executableFile);
			} else {
				fileSetBuilder.file(file.type(), new File(FilesToExtract.fileName(file)));
			}
		}

		IExtractedFileSet extractedFileSet;
		if (!foundExecutable) {
			// we found no executable, so we trigger extraction and hope for the best
			try {
				extractedFileSet = baseStore.extractFileSet(distribution);
			} catch (FileAlreadyExistsException fx) {
				throw new RuntimeException("extraction to "+destinationDir+" has failed", fx);
			}
		} else {
			extractedFileSet = fileSetBuilder.build();
		}

		return ExtractedFileSets.copy(extractedFileSet, temp.getDirectory(), temp.getExecutableNaming());
	}

	private static IDirectory withDistribution(final IDirectory dir, final Distribution distribution) {
		return new IDirectory() {
			
			@Override
			public boolean isGenerated() {
				return dir.isGenerated();
			}
			
			@Override
			public File asFile() {
				File file = new File(dir.asFile(), asPath(distribution));
				if (!file.exists()) {
					if (!file.mkdirs()) {
						throw new RuntimeException("could not create dir "+file);
					}
				}
				return file;
			}

		};
	}

	static String asPath(Distribution distribution) {
		return new StringBuilder()
			.append(distribution.getPlatform().name())
			.append("-")
			.append(distribution.getBitsize().name())
			.append("--")
			.append(distribution.getVersion().asInDownloadPath())
			.toString();
	}
	
	@Override
	public void removeFileSet(Distribution distribution, IExtractedFileSet files) {
		ExtractedFileSets.delete(files);
	}

}
