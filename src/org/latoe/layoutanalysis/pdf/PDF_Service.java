package org.latoe.layoutanalysis.pdf;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;

import org.latoe.layoutanalysis.pdf.labelisation.Service_CRF;
import org.latoe.layoutanalysis.pdf.pdfobject.Chunk_PDF;
import org.latoe.layoutanalysis.pdf.pdfobject.Corpus_PDF;
import org.latoe.layoutanalysis.pdf.pdfobject.Document_PDF;
import org.latoe.layoutanalysis.pdf.pdfobject.Page_PDF;
import org.latoe.layoutanalysis.pdf.pdfobject.Word_PDF;
import org.melodi.objectslogic.Chunk_Lara;
import org.melodi.objectslogic.Document_Lara;
import org.melodi.reader.service.Reader_Service;
import org.melodi.tools.tree.ShiftReduce_Service;

import edu.isi.bmkeg.lapdf.bin.Blockify;
import edu.isi.bmkeg.lapdf.bin.ImagifyBlocks;

public class PDF_Service {

	private static ArrayList<Chunk_Lara> currListChunk;
	private static boolean printFlag = false;

	public PDF_Service() {

	}

	public Document_Lara getDocument(String path, String path_model)
			throws Exception {

		// 3 steps :
		/*
		 * 1. LaPDF Layout
		 */
		String[] args2 = new String[1];
		args2[0] = path;
		Blockify.main(args2);
		
		String[] args3 = new String[2];
		args3[0] = path;
		args3[1] = "./output/";
		ImagifyBlocks.main(args3);
		
		//TODO : use rules for header/footer/footnote/etc.
//		BlockifyClassify.main()

		path = path.replaceAll(".pdf", "_spatial.xml");
		Corpus_PDF corpus = new Corpus_PDF();
		corpus.loadCorpus(path);

		/*
		 * 2. Label
		 */
		Service_CRF crf_model;
		System.out.println("Deserialize model from " + path_model);
		ObjectInputStream in = new ObjectInputStream(new FileInputStream(
				path_model));
		crf_model = (Service_CRF) in.readObject();
		in.close();
		crf_model.predict(corpus, "./output/");

		/*
		 * 3. Logical Tree
		 */
		ArrayList<Document_Lara> array_to_return = new ArrayList<Document_Lara>();
		for (Document_PDF currDocument : corpus.getListdoc()) {
			Document_Lara document_Lara = transform(currDocument);
			array_to_return.add(document_Lara);
		}

		if (array_to_return.size() > 1) {
			System.err.println("Warning : multiples document lara generated");
		}

		return array_to_return.get(0);
	}

	public static void ocrPDF(String path) throws Exception {

		String[] args2 = new String[1];
		args2[0] = "./data/training_label_layout/newPDF/";
		Blockify.main(args2);
		ImagifyBlocks.main(args2);
	}

	public static void trainNewModel(String corpus_training,
			String output_model_path) throws Exception {

		// load annotated data
		Corpus_PDF corpus_train = new Corpus_PDF();
		corpus_train.loadCorpus(corpus_training);

		// train
		Service_CRF myCRF = new Service_CRF(
				"./configuration/PDFParsing/crf.conf");
		myCRF.train(corpus_train, "./output/");

		// serialize
		System.out.println("Serialize model to " + output_model_path);
		ObjectOutputStream out_train = new ObjectOutputStream(
				new FileOutputStream(output_model_path));
		out_train.writeObject(myCRF);
		out_train.close();

	}

	public static Document_Lara transform(Document_PDF documentLayout) {

		Document_Lara currDocument = new Document_Lara();
		
		currDocument.setName(documentLayout.getName());
		ArrayList<Chunk_Lara> arraylist = new ArrayList<Chunk_Lara>();
		Chunk_Lara root_Chunk = new Chunk_Lara(0, 0, 0, 0);
		root_Chunk.setType("root");
		root_Chunk.setDepRel("");
		root_Chunk.setDepId(-1);
		root_Chunk.setText(documentLayout.getName());
		arraylist.add(root_Chunk);

		Chunk_Lara previous_chunk = new Chunk_Lara(0, 0, 0, 0);
		previous_chunk.setType("undefined");

		for (Page_PDF currPage : documentLayout.pages) {
			for (Chunk_PDF currChunk : currPage.groupes) {

				String type_curr = currChunk.getPredictTag();

				if (!type_curr.equals("byline") && !type_curr.contains("other")
						&& !type_curr.contains("footer")
						&& !type_curr.contains("header")) {

					Chunk_Lara newChunk = new Chunk_Lara(0, 0, 0, 0);

					String text = "";
					for (Word_PDF currWord : currChunk.mots) {
						text += currWord.mot + " ";
					}

					newChunk.setText(text);
					newChunk.setType(currChunk.getPredictTag());

					if (previous_chunk.getType().equals("p")
							&& newChunk.getType().equals("p")) {

						// if previous ends by a ponct.
						String previous = previous_chunk.getText();
						int length_previous = previous.length();
						String last_3_previous = previous
								.substring(length_previous - 3);

						if (!last_3_previous.contains(".")
								&& !last_3_previous.contains("?")
								&& !last_3_previous.contains("!")
								&& !last_3_previous.trim().matches("[0-9]*")) {

							// begin with lower
							String newString = newChunk.getText();
							String fist = newString.substring(0, 1);

							if (fist.matches("[a-z]")) {
								// merge
								String initial = previous_chunk.getText();
								String nouveau = newChunk.getText();

								previous_chunk.setText(initial + " " + nouveau);

							} else {
								arraylist.add(newChunk);
							}
						} else {
							arraylist.add(newChunk);
						}

					} else {
						arraylist.add(newChunk);
					}
					previous_chunk = newChunk;
				}
			}
		}

		int index = 0;
		for (Chunk_Lara currChunk : arraylist) {
			currChunk.setId(index);
			index++;
		}

		currDocument.setChunk(arraylist);
		ShiftReduce_Service shiftReduce_Service = new ShiftReduce_Service();
		currDocument = shiftReduce_Service.assign_shiftreduce(currDocument, 0);
		return currDocument;
	}

}