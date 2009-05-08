package org.tigris.mbt;

public class AbstractElement {

	private String labelKey = new String();
	private String fullLabelKey = new String();
	private String reqTagKey = new String();
	private String parameterKey = new String();
	private Integer visitedKey = new Integer(0);
	private boolean backtrackKey = false;
	private String actionsKey = new String();
	private Integer indexKey = new Integer(0);
	private String idKey = new String();
	private String fileKey = new String();
	private boolean mergeKey = false;
	private boolean noMergeKey = false;
	private boolean blockedKey = false;
	private String imageKey = new String();
	private String widthKey = new String();
	private String heightKey = new String();
	private String xPosKey = new String();
	private String yPosKey = new String();
	private boolean mergedMbtKey = false;

	public AbstractElement() {
	}
	
	public AbstractElement( AbstractElement ae ) {
		this.labelKey = ae.labelKey;
		this.fullLabelKey = ae.fullLabelKey;
		this.reqTagKey = ae.reqTagKey;
		this.parameterKey = ae.parameterKey;
		this.visitedKey = ae.visitedKey;
		this.backtrackKey = ae.backtrackKey;
		this.actionsKey = ae.actionsKey;
		this.indexKey = ae.indexKey;
		this.idKey = ae.idKey;
		this.fileKey = ae.fileKey;
		this.mergeKey = ae.mergeKey;
		this.noMergeKey = ae.noMergeKey;
		this.blockedKey = ae.blockedKey;
		this.imageKey = ae.imageKey;
		this.widthKey = ae.widthKey;
		this.heightKey = ae.heightKey;
		this.xPosKey = ae.xPosKey;
		this.yPosKey = ae.yPosKey;
		this.mergedMbtKey = ae.mergedMbtKey;
	}

	public AbstractElement( AbstractElement A, AbstractElement B ) {
		if ( A.fullLabelKey.length() > B.fullLabelKey.length() )
		{
			this.labelKey = A.labelKey;
			this.fullLabelKey = A.fullLabelKey;
			this.reqTagKey = A.reqTagKey;
			this.parameterKey = A.parameterKey;
			this.visitedKey = A.visitedKey;
			this.backtrackKey = A.backtrackKey;
			this.actionsKey = A.actionsKey;
			this.indexKey = A.indexKey;
			this.idKey = A.idKey;
			this.fileKey = A.fileKey;
			this.mergeKey = A.mergeKey;
			this.noMergeKey = A.noMergeKey;
			this.blockedKey = A.blockedKey;
			this.imageKey = A.imageKey;
			this.widthKey = A.widthKey;
			this.heightKey = A.heightKey;
			this.xPosKey = A.xPosKey;
			this.yPosKey = A.yPosKey;
			this.mergedMbtKey = A.mergedMbtKey;
		}
		else
		{
			this.labelKey = B.labelKey;
			this.fullLabelKey = B.fullLabelKey;
			this.reqTagKey = B.reqTagKey;
			this.parameterKey = B.parameterKey;
			this.visitedKey = B.visitedKey;
			this.backtrackKey = B.backtrackKey;
			this.actionsKey = B.actionsKey;
			this.indexKey = B.indexKey;
			this.idKey = B.idKey;
			this.fileKey = B.fileKey;
			this.mergeKey = B.mergeKey;
			this.noMergeKey = B.noMergeKey;
			this.blockedKey = B.blockedKey;
			this.imageKey = B.imageKey;
			this.widthKey = B.widthKey;
			this.heightKey = B.heightKey;
			this.xPosKey = B.xPosKey;
			this.yPosKey = B.yPosKey;
			this.mergedMbtKey = B.mergedMbtKey;
		}
	}

	public boolean isMergedMbtKey() {
		return mergedMbtKey;
	}

	public void setMergedMbtKey(boolean mergedMbtKey) {
		this.mergedMbtKey = mergedMbtKey;
	}

	public String getWidthKey() {
		return widthKey;
	}

	public void setWidthKey(String widthKey) {
		this.widthKey = widthKey;
	}

	public String getHeightKey() {
		return heightKey;
	}

	public void setHeightKey(String heightKey) {
		this.heightKey = heightKey;
	}

	public String getXPosKey() {
		return xPosKey;
	}

	public void setXPosKey(String posKey) {
		xPosKey = posKey;
	}

	public String getYPosKey() {
		return yPosKey;
	}

	public void setYPosKey(String posKey) {
		yPosKey = posKey;
	}

	public String getImageKey() {
		return imageKey;
	}

	public void setImageKey(String imageKey) {
		this.imageKey = imageKey;
	}

	public boolean isBlockedKey() {
		return blockedKey;
	}

	public void setBlockedKey(boolean blockedKey) {
		this.blockedKey = blockedKey;
	}

	public boolean isNoMergeKey() {
		return noMergeKey;
	}

	public void setNoMergeKey(boolean noMergeKey) {
		this.noMergeKey = noMergeKey;
	}

	public boolean isMergeKey() {
		return mergeKey;
	}

	public void setMergeKey(boolean mergeKey) {
		this.mergeKey = mergeKey;
	}

	public String getFileKey() {
		return fileKey;
	}

	public void setFileKey(String fileKey) {
		this.fileKey = fileKey;
	}

	public String getIdKey() {
		return idKey;
	}

	public void setIdKey(String idKey) {
		this.idKey = idKey;
	}

	public Integer getIndexKey() {
		return indexKey;
	}

	public void setIndexKey(Integer indexKey) {
		this.indexKey = indexKey;
	}

	public String getActionsKey() {
		return actionsKey;
	}

	public void setActionsKey(String actionsKey) {
		this.actionsKey = actionsKey;
	}

	public boolean isBacktrackKey() {
		return backtrackKey;
	}

	public void setBacktrackKey(boolean backtrackKey) {
		this.backtrackKey = backtrackKey;
	}

	public Integer getVisitedKey() {
		return visitedKey;
	}

	public void setVisitedKey(Integer visitedKey) {
		this.visitedKey = visitedKey;
	}

	public String getParameterKey() {
		return parameterKey;
	}

	public void setParameterKey(String parameterKey) {
		this.parameterKey = parameterKey;
	}

	public String getReqTagKey() {
		return reqTagKey;
	}

	public void setReqTagKey(String reqTagKey) {
		this.reqTagKey = reqTagKey;
	}

	public String getFullLabelKey() {
		return fullLabelKey;
	}

	public void setFullLabelKey(String fullLabelKey) {
		this.fullLabelKey = fullLabelKey;
	}

	public String getLabelKey() {
		return labelKey;
	}

	public void setLabelKey(String labelKey) {
		this.labelKey = labelKey;
	}
	
	public String toString() {
		return Util.getCompleteName(this);
	}
}
